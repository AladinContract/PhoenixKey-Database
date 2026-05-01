package com.magiclamp.phoenixkey_db.dto.cardano;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * W3C DID Core — Verification Method.
 *
 * <a href="https://www.w3.org/TR/did-core/#verification-methods">spec</a>
 *
 * <p>Override naming strategy về camelCase (W3C spec compliance) — không kế
 * thừa SNAKE_CASE global của app.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record W3CVerificationMethod(
        String id,
        String type,
        String controller,
        @JsonProperty("publicKeyHex") String publicKeyHex) {
}
