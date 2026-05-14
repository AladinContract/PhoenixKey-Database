package com.magiclamp.phoenixkey_db.dto.username;

import java.time.OffsetDateTime;

/**
 * Response after setting or changing a username.
 * Jackson global SNAKE_CASE handles field naming — no @JsonProperty needed.
 */
public record UsernameSetResponse(
        String username,
        OffsetDateTime setAt,
        OffsetDateTime changeableAfter
) {}
