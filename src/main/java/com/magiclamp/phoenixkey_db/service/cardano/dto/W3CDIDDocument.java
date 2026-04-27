package com.magiclamp.phoenixkey_db.service.cardano.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * W3C DID Core — DID Document.
 *
 * <a href="https://www.w3.org/TR/did-core/#did-document-properties">spec</a>
 *
 * <p>Lưu trữ: serialize JSON → đặt vào inline datum của UTxO trên Cardano.
 * Genesis tx hash của UTxO = ID nhúng vào DID string.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record W3CDIDDocument(
        @JsonProperty("@context") List<String> context,
        String id,
        String controller,
        List<W3CVerificationMethod> verificationMethod,
        List<String> authentication,
        List<String> assertionMethod,
        List<String> capabilityInvocation,
        String created,
        String updated) {

    public static final String CONTEXT_V1 = "https://www.w3.org/ns/did/v1";
    public static final String KEY_TYPE_ED25519 = "Ed25519VerificationKey2018";
    public static final String KEY_TYPE_SECP256K1 = "EcdsaSecp256k1VerificationKey2019";
}
