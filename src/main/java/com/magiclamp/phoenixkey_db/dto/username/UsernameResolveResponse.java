package com.magiclamp.phoenixkey_db.dto.username;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UsernameResolveResponse(
        String username,

        @JsonProperty("user_did")
        String userDid
) {}
