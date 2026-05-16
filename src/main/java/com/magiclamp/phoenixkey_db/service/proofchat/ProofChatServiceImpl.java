package com.magiclamp.phoenixkey_db.service.proofchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * ProofChat HTTP client. Calls ProofChat backend (server-to-server) to create
 * a chat session, returns embed URL (with short-lived JWT) for browser.
 *
 * <p>Implements graceful fallback: if PROOFCHAT_BASE_URL is unset (e.g. dev
 * machine), returns a placeholder URL — callers should still display the chat
 * panel and degrade UX gracefully.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProofChatServiceImpl implements ProofChatService {

    private final ObjectMapper objectMapper;

    @Value("${phoenixkey.proofchat.base-url:}")
    private String baseUrl;

    @Value("${phoenixkey.proofchat.api-key:}")
    private String apiKey;

    @Value("${phoenixkey.proofchat.session-ttl-minutes:30}")
    private int sessionTtlMinutes;

    @Override
    public ProofChatSession openSession(String userDid, String agentDid,
                                        String walletAddress, String intent, String contextId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            // Dev fallback — no ProofChat configured
            log.warn("PROOFCHAT_BASE_URL not configured — returning placeholder session");
            return new ProofChatSession(
                    "stub-" + contextId,
                    "https://chat.placeholder.local/?ctx=" + contextId,
                    Instant.now().plus(Duration.ofMinutes(sessionTtlMinutes)).getEpochSecond()
            );
        }

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        try {
            JsonNode response = client.post()
                    .uri("/api/internal/session")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "user_did", userDid,
                            "agent_did", agentDid,
                            "wallet_address", walletAddress,
                            "intent", intent,
                            "context_id", contextId,
                            "ttl_seconds", sessionTtlMinutes * 60
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new RuntimeException("ProofChat returned empty body");
            }

            return new ProofChatSession(
                    response.path("session_id").asText(),
                    response.path("embed_url").asText(),
                    response.path("expires_at").asLong()
            );
        } catch (Exception e) {
            log.error("ProofChat session open failed: {}", e.getMessage());
            throw new RuntimeException("ProofChat unavailable: " + e.getMessage(), e);
        }
    }
}
