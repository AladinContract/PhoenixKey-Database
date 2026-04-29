package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Misc spec endpoints (Phase E.4) — đa phần MVP stub:
 * <ul>
 *   <li>{@code GET /tx/estimate} — Cardano fee estimate (hardcode map)</li>
 *   <li>{@code GET /api/v1/identity/nodes} — LampNet node map (12 mock nodes)</li>
 *   <li>{@code POST /support/session/init} — Get LAMP support session (placeholder URL)</li>
 * </ul>
 *
 * <p>Phase H sẽ wire data thật khi LampNet API + ProofChat SDK + BloxBean fee
 * calculator sẵn sàng.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Misc", description = "Endpoint phụ cho dashboard / get-LAMP (MVP stub)")
public class MiscController {

    private static final String STATUS_ACTIVE = "active";

    private final UuidGenerator uuidGenerator;

    // ──────────────────────────────────────────────────────────────
    // GET /tx/estimate?type=key_rotation
    // ──────────────────────────────────────────────────────────────

    /** Hardcoded fee map (đơn vị: ADA tương đương MAGIC theo plan E.5). */
    private static final Map<String, Long> FEE_ESTIMATES_MAGIC = Map.of(
            "key_rotation", 12L,
            "create_did", 12L,
            "update_did", 12L,
            "seed_export", 0L,
            "guardian_add", 8L,
            "guardian_remove", 8L);

    @Operation(summary = "Estimate tx fee (MVP hardcode)", description = """
            MVP: trả constant theo type. Phase H sẽ thay bằng BloxBean fee calculator
            real-time (build skeleton tx → calculate exact fee từ protocol params).
            """)
    @ApiResponse(responseCode = "200", description = "Fee estimate", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/tx/estimate")
    public ResponseEntity<DataResponse<TxEstimate>> estimate(
            @Parameter(description = "key_rotation | create_did | update_did | seed_export | guardian_add | guardian_remove")
            @RequestParam("type") String type) {
        long magic = FEE_ESTIMATES_MAGIC.getOrDefault(type, 12L);
        TxEstimate result = new TxEstimate(type, magic, "MAGIC");
        return ResponseEntity.ok(
                DataResponse.<TxEstimate>builder()
                        .code(1000)
                        .message("Estimated fee")
                        .result(result)
                        .build());
    }

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/identity/nodes?did=...
    // ──────────────────────────────────────────────────────────────

    /**
     * 12 LampNet node mock (vị trí địa lý). Spec §14.4 — dashboard canvas
     * animation. MVP stub vì LampNet API chưa public — Long quyết data source thật
     * sau (xem PLAN-Server.md "Decisions deferred").
     */
    private static final List<NodeMock> MOCK_NODES = List.of(
            new NodeMock("SG", "Singapore", 1.3521, 103.8198, STATUS_ACTIVE),
            new NodeMock("JP", "Tokyo", 35.6762, 139.6503, STATUS_ACTIVE),
            new NodeMock("US", "San Francisco", 37.7749, -122.4194, STATUS_ACTIVE),
            new NodeMock("VN", "Hanoi", 21.0285, 105.8542, STATUS_ACTIVE),
            new NodeMock("DE", "Frankfurt", 50.1109, 8.6821, STATUS_ACTIVE),
            new NodeMock("AU", "Sydney", -33.8688, 151.2093, STATUS_ACTIVE),
            new NodeMock("IN", "Mumbai", 19.0760, 72.8777, STATUS_ACTIVE),
            new NodeMock("UK", "London", 51.5074, -0.1278, STATUS_ACTIVE),
            new NodeMock("BR", "Sao Paulo", -23.5505, -46.6333, STATUS_ACTIVE),
            new NodeMock("FR", "Paris", 48.8566, 2.3522, STATUS_ACTIVE),
            new NodeMock("CA", "Toronto", 43.6511, -79.3470, STATUS_ACTIVE),
            new NodeMock("KR", "Seoul", 37.5665, 126.9780, STATUS_ACTIVE));

    @Operation(summary = "LampNet node map (MVP stub: 12 mock nodes)", description = """
            Trả 12 node mock cho dashboard canvas animation (spec §14.4).
            Phase H: wire LampNet API thật khi public, hoặc query metadata từ
            Cardano stake pools.
            """)
    @GetMapping("/identity/nodes")
    public ResponseEntity<DataResponse<NodesResponse>> nodes(
            @Parameter(description = "DID của user (chưa dùng MVP — sẽ filter node theo proximity Phase H)")
            @RequestParam(value = "did", required = false) String did) {
        // did param chưa được dùng — Phase H sẽ tính proximity dựa trên user location.
        return ResponseEntity.ok(
                DataResponse.<NodesResponse>builder()
                        .code(1000)
                        .message("LampNet nodes")
                        .result(new NodesResponse(MOCK_NODES, MOCK_NODES.size()))
                        .build());
    }

    // ──────────────────────────────────────────────────────────────
    // POST /support/session/init
    // ──────────────────────────────────────────────────────────────

    @Operation(summary = "Init Get LAMP support session (MVP stub)", description = """
            MVP: trả URL placeholder. Phase H integrate ProofChat SDK + Genie pool
            routing (spec §15.8).
            """)
    @PostMapping("/support/session/init")
    public ResponseEntity<DataResponse<SupportSessionResponse>> supportSessionInit() {
        String supportSessionId = uuidGenerator.create().toString();
        SupportSessionResponse result = new SupportSessionResponse(
                supportSessionId,
                "https://proofchat.aladin.work/session/" + supportSessionId,
                "ProofChat SDK chưa integrate (Phase H). URL trên là placeholder.");
        return ResponseEntity.ok(
                DataResponse.<SupportSessionResponse>builder()
                        .code(1000)
                        .message("Support session created (stub)")
                        .result(result)
                        .build());
    }

    // ──────────────────────────────────────────────────────────────
    // Inline DTO records
    // ──────────────────────────────────────────────────────────────

    public record TxEstimate(String type, long magic, String unit) {
    }

    public record NodeMock(String code, String city, double lat, double lng, String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodesResponse(List<NodeMock> nodes, int total) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SupportSessionResponse(String sessionId, String proofchatUrl, String note) {
    }
}
