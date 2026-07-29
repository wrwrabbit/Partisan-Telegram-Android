/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include <cstring>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include "Config.h"
#include "ConnectionsManager.h"
#include "FileLog.h"
#include "BuffersStorage.h"

// Encrypted tgnet.dat starts with a uniformly random uint32 >= this value; legacy plaintext files
// start with a tiny size prefix, so no fixed bits are needed to tell them apart.
static const uint32_t CONFIG_ENC_MARKER_MIN = 0x40000000; // 1024 MiB
static const int CONFIG_ENC_MARKER_LEN = 4;
static const int CONFIG_ENC_IV_LEN = 12;
static const int CONFIG_ENC_CIPHERLEN_LEN = 4;
static const int CONFIG_ENC_TAG_LEN = 16;
static const int CONFIG_ENC_HEADER_LEN = CONFIG_ENC_MARKER_LEN + CONFIG_ENC_IV_LEN + CONFIG_ENC_CIPHERLEN_LEN;

static uint32_t generateEncryptionMarker() {
    uint32_t value = 0;
    for (int attempt = 0; attempt < 32; attempt++) {
        if (RAND_bytes((uint8_t *) &value, sizeof(value)) == 1 && value >= CONFIG_ENC_MARKER_MIN) {
            return value;
        }
    }
    return CONFIG_ENC_MARKER_MIN; // expected ~1.3 attempts; this is unreachable in practice
}

static bool aesGcmEncrypt(const uint8_t *key, const uint8_t *plaintext, int plaintextLen,
                          uint8_t *ivOut, uint8_t *ciphertextOut, uint8_t *tagOut) {
    if (RAND_bytes(ivOut, CONFIG_ENC_IV_LEN) != 1) {
        return false;
    }
    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    if (ctx == nullptr) {
        return false;
    }
    bool ok = false;
    do {
        if (EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_IVLEN, CONFIG_ENC_IV_LEN, nullptr) != 1
                || EVP_EncryptInit_ex(ctx, nullptr, nullptr, key, ivOut) != 1) {
            break;
        }
        int len = 0;
        if (EVP_EncryptUpdate(ctx, ciphertextOut, &len, plaintext, plaintextLen) != 1) {
            break;
        }
        int total = len;
        if (EVP_EncryptFinal_ex(ctx, ciphertextOut + total, &len) != 1) {
            break;
        }
        total += len;
        if (total != plaintextLen // GCM adds no padding
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_GET_TAG, CONFIG_ENC_TAG_LEN, tagOut) != 1) {
            break;
        }
        ok = true;
    } while (false);
    EVP_CIPHER_CTX_free(ctx);
    return ok;
}

static bool aesGcmDecrypt(const uint8_t *key, const uint8_t *iv, const uint8_t *ciphertext,
                          int ciphertextLen, const uint8_t *tag, uint8_t *plaintextOut) {
    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    if (ctx == nullptr) {
        return false;
    }
    bool ok = false;
    do {
        if (EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_IVLEN, CONFIG_ENC_IV_LEN, nullptr) != 1
                || EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, iv) != 1) {
            break;
        }
        int len = 0;
        if (EVP_DecryptUpdate(ctx, plaintextOut, &len, ciphertext, ciphertextLen) != 1
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_TAG, CONFIG_ENC_TAG_LEN, (void *) tag) != 1) {
            break;
        }
        int finalLen = 0;
        if (EVP_DecryptFinal_ex(ctx, plaintextOut + len, &finalLen) != 1) {
            break; // 0 on tag mismatch
        }
        ok = true;
    } while (false);
    EVP_CIPHER_CTX_free(ctx);
    return ok;
}

static NativeByteBuffer *readEncryptedConfig(FILE *file, long fileSize, const uint8_t *key) {
    if (key == nullptr) {
        return nullptr;
    }
    if (fseek(file, 0, SEEK_SET) || fseek(file, CONFIG_ENC_MARKER_LEN, SEEK_CUR)) {
        return nullptr;
    }
    uint8_t iv[CONFIG_ENC_IV_LEN];
    uint32_t cipherLen = 0;
    if (fread(iv, sizeof(uint8_t), CONFIG_ENC_IV_LEN, file) != CONFIG_ENC_IV_LEN
            || fread(&cipherLen, sizeof(uint32_t), 1, file) != 1
            || cipherLen == 0
            || (long) cipherLen > fileSize - CONFIG_ENC_HEADER_LEN - CONFIG_ENC_TAG_LEN) {
        return nullptr;
    }
    auto *ciphertext = new uint8_t[cipherLen];
    uint8_t tag[CONFIG_ENC_TAG_LEN];
    NativeByteBuffer *buffer = nullptr;
    if (fread(ciphertext, sizeof(uint8_t), cipherLen, file) == cipherLen
            && fread(tag, sizeof(uint8_t), CONFIG_ENC_TAG_LEN, file) == CONFIG_ENC_TAG_LEN) {
        buffer = BuffersStorage::getInstance().getFreeBuffer(cipherLen);
        if (!aesGcmDecrypt(key, iv, ciphertext, (int) cipherLen, tag, buffer->bytes())) {
            buffer->reuse();
            buffer = nullptr;
        }
    }
    delete[] ciphertext;
    return buffer;
}

static bool writeEncryptedPayload(FILE *file, const uint8_t *key, const uint8_t *plaintext, uint32_t plaintextLen) {
    uint8_t iv[CONFIG_ENC_IV_LEN];
    uint8_t tag[CONFIG_ENC_TAG_LEN];
    auto *ciphertext = new uint8_t[plaintextLen];
    bool ok = false;
    if (aesGcmEncrypt(key, plaintext, (int) plaintextLen, iv, ciphertext, tag)) {
        uint32_t marker = generateEncryptionMarker();
        ok = fwrite(&marker, sizeof(uint32_t), 1, file) == 1
                && fwrite(iv, sizeof(uint8_t), CONFIG_ENC_IV_LEN, file) == CONFIG_ENC_IV_LEN
                && fwrite(&plaintextLen, sizeof(uint32_t), 1, file) == 1
                && fwrite(ciphertext, sizeof(uint8_t), plaintextLen, file) == plaintextLen
                && fwrite(tag, sizeof(uint8_t), CONFIG_ENC_TAG_LEN, file) == CONFIG_ENC_TAG_LEN;
    }
    delete[] ciphertext;
    return ok;
}

Config::Config(int32_t instance, std::string fileName) {
    instanceNum = instance;
    configPath = ConnectionsManager::getInstance(instanceNum).currentConfigPath + fileName;
    backupPath = configPath + ".bak";
    FILE *backup = fopen(backupPath.c_str(), "rb");
    if (backup != nullptr) {
        if (LOGS_ENABLED) DEBUG_D("Config(%p, %s) backup file found %s", this, configPath.c_str(), backupPath.c_str());
        fclose(backup);
        remove(configPath.c_str());
        rename(backupPath.c_str(), configPath.c_str());
    }
}

NativeByteBuffer *Config::readConfig() {
    NativeByteBuffer *buffer = nullptr;
    FILE *file = fopen(configPath.c_str(), "rb");
    if (file != nullptr) {
        fseek(file, 0, SEEK_END);
        long fileSize = ftell(file);
        if (fseek(file, 0, SEEK_SET)) {
            if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) failed fseek to begin, reopen it", this, configPath.c_str());
            fclose(file);
            file = fopen(configPath.c_str(), "rb");
        }
        uint32_t marker = 0;
        if (file != nullptr && fileSize >= (long) sizeof(marker)
                && fread(&marker, sizeof(uint32_t), 1, file) == 1
                && marker >= CONFIG_ENC_MARKER_MIN) {
            ConnectionsManager &manager = ConnectionsManager::getInstance(instanceNum);
            buffer = readEncryptedConfig(file, fileSize, manager.configEncryptionKeySet ? manager.configEncryptionKey : nullptr);
            fclose(file);
            return buffer;
        }
        if (file != nullptr) {
            fseek(file, 0, SEEK_SET);
        }
        uint32_t size = 0;
        size_t bytesRead = fread(&size, sizeof(uint32_t), 1, file);
        if (LOGS_ENABLED) DEBUG_D("Config(%p, %s) load, size = %u, fileSize = %u", this, configPath.c_str(), size, (uint32_t) fileSize);
        if (bytesRead > 0 && size > 0 && (int32_t) size < fileSize) {
            buffer = BuffersStorage::getInstance().getFreeBuffer(size);
            if (fread(buffer->bytes(), sizeof(uint8_t), size, file) != size) {
                buffer->reuse();
                buffer = nullptr;
            }
        }
        fclose(file);
    }
    return buffer;
}

void Config::writeConfig(NativeByteBuffer *buffer) {
    if (LOGS_ENABLED) DEBUG_D("Config(%p, %s) start write config", this, configPath.c_str());
    ConnectionsManager &manager = ConnectionsManager::getInstance(instanceNum);
    if (manager.configEncryptionEncryptOnWrite && !manager.configEncryptionKeySet) {
        // Encryption is enabled but the key is temporarily unavailable (e.g. keystore locked at boot).
        // Skip the write so the existing encrypted file is preserved for decryption once the key returns.
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) skip write: encryption key unavailable", this, configPath.c_str());
        return;
    }
    FILE *file = fopen(configPath.c_str(), "rb");
    FILE *backup = fopen(backupPath.c_str(), "rb");
    bool error = false;
    bool hasBackupFile = false;
    if (file != nullptr) {
        if (backup == nullptr) {
            fclose(file);
            if (rename(configPath.c_str(), backupPath.c_str()) != 0) {
                if (LOGS_ENABLED) DEBUG_E("Config(%p) unable to rename file %s to backup file %s", this, configPath.c_str(), backupPath.c_str());
                error = true;
            } else {
                hasBackupFile = true;
            }
        } else {
            fclose(file);
            fclose(backup);
            remove(configPath.c_str());
        }
    }
    if (error) {
        return;
    }
    file = fopen(configPath.c_str(), "wb");
    if (chmod(configPath.c_str(), 0660)) {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) chmod failed", this, configPath.c_str());
    }
    if (file == nullptr) {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) unable to open file for writing", this, configPath.c_str());
        return;
    }
    uint32_t size = buffer->position();
    if (manager.configEncryptionKeySet && manager.configEncryptionEncryptOnWrite) {
        if (!writeEncryptedPayload(file, manager.configEncryptionKey, buffer->bytes(), size)) {
            if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) failed to write encrypted config data to file", this, configPath.c_str());
            error = true;
        }
    } else if (fwrite(&size, sizeof(uint32_t), 1, file) == 1) {
        if (fwrite(buffer->bytes(), sizeof(uint8_t), size, file) != size) {
            if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) failed to write config data to file", this, configPath.c_str());
            error = true;
        }
    } else {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) failed to write config size to file", this, configPath.c_str());
        error = true;
    }
    if (fflush(file)) {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) fflush failed", this, configPath.c_str());
        error = true;
    }
    int fd = fileno(file);
    if (fd == -1) {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) fileno failed", this, configPath.c_str());
        error = true;
    } else {
        if (LOGS_ENABLED) DEBUG_D("Config(%p, %s) fileno = %d", this, configPath.c_str(), fd);
    }
    if (fd != -1 && fsync(fd) == -1) {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) fsync failed", this, configPath.c_str());
        error = true;
    }
    if (fclose(file)) {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) fclose failed", this, configPath.c_str());
        error = true;
    }
    if (error) {
        if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) failed to write config", this, configPath.c_str());
        if (remove(configPath.c_str())) {
            if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) remove config failed", this, configPath.c_str());
        }
    } else {
        if (hasBackupFile && remove(backupPath.c_str())) {
            if (LOGS_ENABLED) DEBUG_E("Config(%p, %s) remove backup failed, %s", this, backupPath.c_str(), strerror(errno));
        }
    }
    if (!error) {
        if (LOGS_ENABLED) DEBUG_D("Config(%p, %s) config write ok", this, configPath.c_str());
    }
}
