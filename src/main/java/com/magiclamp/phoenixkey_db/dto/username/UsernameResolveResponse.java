package com.magiclamp.phoenixkey_db.dto.username;

/**
 * Public lookup result: username → DID.
 * Jackson global SNAKE_CASE handles field naming — no @JsonProperty needed.
 */
public record UsernameResolveResponse(
        String username,
        String userDid
) {}
