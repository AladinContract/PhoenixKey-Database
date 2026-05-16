package com.magiclamp.phoenixkey_db.service.recovery;

import com.magiclamp.phoenixkey_db.dto.recovery.RecoveryDtos.*;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Recovery flow stub.
 *
 * <p><b>TODO (Lợi + Thư):</b> implement once Aiken {@code recovery.ak} validator
 * is deployed. Endpoints currently return UnsupportedOperationException with
 * clear error code so frontend can render "Tính năng đang phát triển".</p>
 *
 * <p>Full design lives in TESTNET-PLAN §3 + phoenixkey-validator/validators/taad.ak
 * (the recovery state machine is part of the TAAD validator).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryServiceImpl implements RecoveryService {

    @Override
    public InitRecoveryResponse init(InitRecoveryRequest request) {
        throw new AppException(ErrorCode.RECOVERY_INVALID_STATE,
                "Recovery flow pending validator deploy");
    }

    @Override
    public void cancel(UUID recoveryId, String userDid, CancelRecoveryRequest request) {
        throw new AppException(ErrorCode.RECOVERY_INVALID_STATE,
                "Recovery flow pending validator deploy");
    }

    @Override
    public FinalizeRecoveryResponse finalize(UUID recoveryId) {
        throw new AppException(ErrorCode.RECOVERY_INVALID_STATE,
                "Recovery flow pending validator deploy");
    }

    @Override
    public RecoveryStatusResponse getStatus(String userDid) {
        return new RecoveryStatusResponse(null, "NONE", 0, null, List.of());
    }
}
