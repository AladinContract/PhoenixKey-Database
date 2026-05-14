package com.magiclamp.phoenixkey_db.security;

/**
 * Resolved authenticated user — injected by {@link AuthArgumentResolver} when
 * a controller method declares {@code AuthenticatedUser auth} as a parameter.
 *
 * <p>Source: {@code Authorization: Bearer <session_token>} header. Token must
 * be of type "session" (24h TTL). Verified by {@link JwtAuthFilter}.</p>
 */
public record AuthenticatedUser(String userDid, String tokenType) {

    public boolean isGenie() {
        // Role check could be elaborated; for now any authenticated user can be Genie if
        // they registered. The /genie/* endpoints handle their own gating.
        return true;
    }
}
