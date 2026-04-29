package com.magiclamp.phoenixkey_db.service.crypto;

/**
 * Verify chữ ký ECDSA secp256k1 từ Hardware Key (Secure Enclave/TEE mobile).
 *
 * Format thống nhất theo PLAN-Server.md:
 * - Curve: secp256k1
 * - Hash: SHA-256
 * - Signature: DER-encoded (sequence of two integers r, s)
 * - Public key: hex-encoded compressed (33 byte: 0x02/0x03 + X) hoặc uncompressed (65 byte: 0x04 + X + Y)
 */
public interface SignatureService {

    /**
     * Verify ECDSA SECP256K1 chữ ký trên message gốc (sẽ tự SHA-256 trước khi verify).
     *
     * @param publicKeyHex hex của public key (compressed 33B hoặc uncompressed 65B)
     * @param message      message gốc (chưa hash) — service sẽ SHA-256 trước
     * @param signature    DER-encoded signature
     * @return true nếu chữ ký hợp lệ
     */
    boolean verifyEcdsa(String publicKeyHex, byte[] message, byte[] signature);

    /**
     * Verify Genesis signature: chữ ký chứng minh user sở hữu private key tương
     * ứng với {@code publicKeyHex}. Message cố định = {@code "PHOENIXKEY_GENESIS:" + publicKeyHex}.
     */
    boolean verifyGenesis(String publicKeyHex, byte[] derSignature);
}
