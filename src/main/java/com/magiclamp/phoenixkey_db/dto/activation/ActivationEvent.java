package com.magiclamp.phoenixkey_db.dto.activation;

/**
 * SSE event payload for the activation stream (`GET /activation/{id}/events`).
 * Event types: "payment_confirmed" | "activated" | "failed" | "expired" | "cancelled"
 */
public record ActivationEvent(String type, Payload data) {
    public record Payload(String status, String txHash, String reason) {}
}
