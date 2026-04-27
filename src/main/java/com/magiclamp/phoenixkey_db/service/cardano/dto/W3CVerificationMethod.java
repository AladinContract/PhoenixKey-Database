package com.magiclamp.phoenixkey_db.service.cardano.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * W3C DID Core — Verification Method.
 *
 * <a href="https://www.w3.org/TR/did-core/#verification-methods">spec</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record W3CVerificationMethod(
        String id,
        String type,
        String controller,
        @JsonProperty("publicKeyHex") String publicKeyHex) {
}
