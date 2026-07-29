#ifndef CONFIGENCRYPTION_H
#define CONFIGENCRYPTION_H

#include <cstdint>
#include <cstdio>
#include "NativeByteBuffer.h"

namespace ConfigEncryption {
    bool isEncryptionMarker(uint32_t marker);
    NativeByteBuffer *readEncryptedConfig(FILE *file, long fileSize, const uint8_t *key);
    bool writeEncryptedPayload(FILE *file, const uint8_t *key, const uint8_t *plaintext, uint32_t plaintextLen);
}

#endif
