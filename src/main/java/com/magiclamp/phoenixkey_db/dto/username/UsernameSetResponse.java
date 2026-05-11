package com.magiclamp.phoenixkey_db.dto.username;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record UsernameSetResponse(
        String username,

        @JsonProperty("set_at")
        OffsetDateTime setAt,

        @JsonProperty("changeable_after")
        OffsetDateTime changeableAfter
) {}
