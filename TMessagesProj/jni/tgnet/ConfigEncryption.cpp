#include <openssl/evp.h>
#include <openssl/rand.h>
#include "ConfigEncryption.h"
#include "BuffersStorage.h"

// Encrypted tgnet.dat starts with a uniformly random uint32 >= this value; legacy plaintext files
// start with a tiny size prefix, so no fixed bits are needed to tell them apart.
static const uint32_t MARKER_MIN = 0x40000000; // 1024 MiB
static const int MARKER_LEN = 4;
static const int IV_LEN = 12;
static const int CIPHERLEN_LEN = 4;
static const int TAG_LEN = 16;
static const int HEADER_LEN = MARKER_LEN + IV_LEN + CIPHERLEN_LEN;

static uint32_t generateMarker() {
    uint32_t value = 0;
    for (int attempt = 0; attempt < 32; attempt++) {
        if (RAND_bytes((uint8_t *) &value, sizeof(value)) == 1 && value >= MARKER_MIN) {
            return value;
        }
    }
    return MARKER_MIN; // expected ~1.3 attempts; this is unreachable in practice
}

static bool aesGcmEncrypt(const uint8_t *key, const uint8_t *plaintext, int plaintextLen,
                          uint8_t *ivOut, uint8_t *ciphertextOut, uint8_t *tagOut) {
    if (RAND_bytes(ivOut, IV_LEN) != 1) {
        return false;
    }
    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    if (ctx == nullptr) {
        return false;
    }
    bool ok = false;
    do {
        if (EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_IVLEN, IV_LEN, nullptr) != 1
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
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_GET_TAG, TAG_LEN, tagOut) != 1) {
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
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_IVLEN, IV_LEN, nullptr) != 1
                || EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, iv) != 1) {
            break;
        }
        int len = 0;
        if (EVP_DecryptUpdate(ctx, plaintextOut, &len, ciphertext, ciphertextLen) != 1
                || EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_TAG, TAG_LEN, (void *) tag) != 1) {
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

bool ConfigEncryption::isEncryptionMarker(uint32_t marker) {
    return marker >= MARKER_MIN;
}

NativeByteBuffer *ConfigEncryption::readEncryptedConfig(FILE *file, long fileSize, const uint8_t *key) {
    if (key == nullptr) {
        return nullptr;
    }
    if (fileSize < (long) (HEADER_LEN + TAG_LEN)) {
        return nullptr;
    }
    if (fseek(file, 0, SEEK_SET) || fseek(file, MARKER_LEN, SEEK_CUR)) {
        return nullptr;
    }
    uint8_t iv[IV_LEN];
    uint32_t cipherLen = 0;
    if (fread(iv, sizeof(uint8_t), IV_LEN, file) != IV_LEN
            || fread(&cipherLen, sizeof(uint32_t), 1, file) != 1
            || cipherLen == 0
            || (uint64_t) cipherLen > (uint64_t) fileSize - HEADER_LEN - TAG_LEN) {
        return nullptr;
    }
    auto *ciphertext = new uint8_t[cipherLen];
    uint8_t tag[TAG_LEN];
    NativeByteBuffer *buffer = nullptr;
    if (fread(ciphertext, sizeof(uint8_t), cipherLen, file) == cipherLen
            && fread(tag, sizeof(uint8_t), TAG_LEN, file) == TAG_LEN) {
        buffer = BuffersStorage::getInstance().getFreeBuffer(cipherLen);
        if (!aesGcmDecrypt(key, iv, ciphertext, (int) cipherLen, tag, buffer->bytes())) {
            buffer->reuse();
            buffer = nullptr;
        }
    }
    delete[] ciphertext;
    return buffer;
}

bool ConfigEncryption::writeEncryptedPayload(FILE *file, const uint8_t *key, const uint8_t *plaintext, uint32_t plaintextLen) {
    uint8_t iv[IV_LEN];
    uint8_t tag[TAG_LEN];
    auto *ciphertext = new uint8_t[plaintextLen];
    bool ok = false;
    if (aesGcmEncrypt(key, plaintext, (int) plaintextLen, iv, ciphertext, tag)) {
        uint32_t marker = generateMarker();
        ok = fwrite(&marker, sizeof(uint32_t), 1, file) == 1
                && fwrite(iv, sizeof(uint8_t), IV_LEN, file) == IV_LEN
                && fwrite(&plaintextLen, sizeof(uint32_t), 1, file) == 1
                && fwrite(ciphertext, sizeof(uint8_t), plaintextLen, file) == plaintextLen
                && fwrite(tag, sizeof(uint8_t), TAG_LEN, file) == TAG_LEN;
    }
    delete[] ciphertext;
    return ok;
}
