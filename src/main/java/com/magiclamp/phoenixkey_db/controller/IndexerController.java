package com.magiclamp.phoenixkey_db.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.request.SyncTaadRequest;
import com.magiclamp.phoenixkey_db.service.IndexerService;

import lombok.RequiredArgsConstructor;

/**
 * Controller cho Indexer Worker — sync trạng thái TAAD từ Blockchain.
 *
 * **CHỈ Indexer Worker được gọi** — không phải từ App hay NestJS.
 *
 * Bảo mật: endpoint này nên được bảo vệ bằng:
 * - Internal network (VPC/private subnet)
 * - Hoặc API key/token riêng cho Indexer Worker
 * (Cấu hình ở tầng API Gateway/Kubernetes NetworkPolicy)
 *
 * SSoT: Blockchain Cardano. PK_DB chỉ là cache.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Tag(name = "Indexer", description = "Internal — Indexer Worker sync TAAD state từ Blockchain")
public class IndexerController {

    private final IndexerService indexerService;

    /**
     * Sync trạng thái TAAD từ Cardano.
     *
     * **Ai gọi:** Indexer Worker (chạy riêng, không phải App/NestJS)
     *
     * Indexer Worker quét Blockchain Cardano → phát hiện thay đổi trạng thái
     * TAAD của một DID → gọi endpoint này để cập nhật cache.
     *
     * **Optimistic Locking:**
     * - `last_synced_block < new_block` → cập nhật cache
     * - `last_synced_block >= new_block` → stale → throw TAAD_STATE_STALE
     * - `block_hash` không khớp → Reorg detected → xóa cache, insert lại
     *
     * **Quy tắc quan trọng:**
     * - Chỉ Indexer Worker được ghi. Không nhận lệnh trực tiếp từ App.
     * - Block mới phải lớn hơn block đang có → chống Blind Overwrite.
     * - Block hash phải khớp → chống Reorg.
     *
     * @param request userDid + pkh + seq + status + block info
     * @return 200 OK
     */
    @Operation(summary = "Sync TAAD state từ Blockchain", description = """
            Indexer Worker gọi sau khi phát hiện thay đổi TAAD state trên Cardano.

            **Use case:**
            - User thay đổi controller → Indexer thấy Tx → gọi sync
            - Guardians approve recovery → Indexer thấy Tx → gọi sync
            - Reorg detected → Indexer thấy hash lệch → xóa cache → sync lại

            **Optimistic Locking:**
            | Tình trạng | Hành xử |
            |---|---|
            | `last_synced_block < new_block` | Cập nhật cache |
            | `last_synced_block >= new_block` | Throw TAAD_STATE_STALE |
            | block_hash lệch (Reorg) | Xóa cache → insert lại |

            **CHỈ Indexer Worker gọi** — không phải App hay NestJS.

            **Bảo mật:** Endpoint nên được bảo vệ bằng:
            - Internal network (VPC/private subnet)
            - Hoặc API key riêng cho Indexer Worker
            """)

    @ApiResponse(responseCode = "200", description = "TAAD state đã sync thành công", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "409", description = "Stale update — block mới không lớn hơn block đang có (bỏ qua)", content = @Content)
    @PostMapping("/sync-taad")
    public ResponseEntity<DataResponse<Void>> syncTaad(@Valid @RequestBody SyncTaadRequest request) {
        indexerService.syncTaad(request);
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("TAAD state synced")
                        .build());
    }
}
