package com.magiclamp.phoenixkey_db.service.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * Generate did:phoenix identifiers theo spec PhoenixKey-SDK/method.md §2.
 *
 * <pre>
 * did-phoenix     = "did:phoenix:" slot-component ":" hash-component
 * slot-component  = 1*( ALPHA / DIGIT )   ; base32-encoded Cardano slot number
 * hash-component  = 64HEXDIG              ; BLAKE2b-256 hash, lowercase hex
 *
 * hash = BLAKE2b-256( encode(entity_type) || (owner_did ?? "root") || encode(slot) || random_256 )
 * did  = "did:phoenix:" || base32_nopad(slot) || ":" || hex(hash)
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DidPhoenixGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DID_PREFIX = "did:phoenix:";

    // RFC 4648 base32 alphabet, lowercase variant for DIDs
    private static final char[] BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();

    /**
     * Generate a fresh did:phoenix identifier.
     *
     * @param entityType  one of "person", "org", "device", "machine", "asset",
     *                    "bot", "ai", "service", "context", "character" (spec §1).
     * @param ownerDid    parent DID for non-human entities. Pass {@code Optional.empty()}
     *                    for root PersonDID.
     * @param slot        current Cardano slot (from Blockfrost tip query).
     * @return            {@code did:phoenix:<base32_slot>:<hex_hash>}
     */
    public String generate(String entityType, Optional<String> ownerDid, long slot) {
        byte[] random256 = new byte[32];
        RANDOM.nextBytes(random256);

        byte[] entityBytes = entityType.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ownerBytes  = ownerDid.orElse("root").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] slotBytes   = encodeSlot(slot);

        Blake2bDigest digest = new Blake2bDigest(256); // 256-bit output
        digest.update(entityBytes, 0, entityBytes.length);
        digest.update(ownerBytes, 0, ownerBytes.length);
        digest.update(slotBytes, 0, slotBytes.length);
        digest.update(random256, 0, random256.length);

        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);

        String did = DID_PREFIX + base32NoPad(slotBytes) + ":" + toHex(hash);
        log.debug("Generated DID for entity={}, slot={}: {}", entityType, slot, did);
        return did;
    }

    /**
     * Encode slot as 8-byte big-endian. Cardano slot fits comfortably in 8 bytes
     * (max practical slot ~2^40 in next ~30 years).
     */
    private byte[] encodeSlot(long slot) {
        return ByteBuffer.allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(slot)
                .array();
    }

    /**
     * Base32 encode without padding (RFC 4648, lowercase alphabet).
     * Strips leading zero bytes from the slot to keep DIDs short — e.g.
     * slot 1 → "aaaaaaq" (7 chars) instead of full 13-char padded encoding.
     */
    String base32NoPad(byte[] data) {
        // Find the first non-zero byte to strip leading zeros (compact representation)
        int start = 0;
        while (start < data.length - 1 && data[start] == 0) start++;
        byte[] trimmed = new byte[data.length - start];
        System.arraycopy(data, start, trimmed, 0, trimmed.length);

        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : trimmed) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(BASE32_ALPHABET[idx]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET[idx]);
        }
        return sb.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Validate did:phoenix format. Used by request validators.
     */
    public static boolean isValid(String did) {
        if (did == null || !did.startsWith(DID_PREFIX)) return false;
        String rest = did.substring(DID_PREFIX.length());
        int sep = rest.indexOf(':');
        if (sep <= 0) return false;
        String slotPart = rest.substring(0, sep);
        String hashPart = rest.substring(sep + 1);
        if (!slotPart.matches("[a-z2-7]+")) return false;
        return hashPart.matches("[0-9a-f]{64}");
    }
}
