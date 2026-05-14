package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.security.AuthenticatedUser;
import com.magiclamp.phoenixkey_db.service.genie.GenieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/genie")
@RequiredArgsConstructor
@Tag(name = "Genie", description = "Genie operator registry — peer agents who facilitate activation packages")
public class GenieController {

    private final GenieService genieService;

    @Operation(summary = "Register as Genie — requires ≥ 1001 LAMP + 10 ADA wallet balance")
    @PostMapping("/register")
    public ResponseEntity<DataResponse<Void>> register(AuthenticatedUser auth) {
        genieService.register(auth.userDid());
        return ResponseEntity.ok(DataResponse.<Void>builder()
                .code(1000).message("Registered as Genie").build());
    }

    @Operation(summary = "Heartbeat — keep Genie status AVAILABLE")
    @PostMapping("/heartbeat")
    public ResponseEntity<DataResponse<Void>> heartbeat(AuthenticatedUser auth) {
        genieService.heartbeat(auth.userDid());
        return ResponseEntity.ok(DataResponse.<Void>builder()
                .code(1000).build());
    }
}
