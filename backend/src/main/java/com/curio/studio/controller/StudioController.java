package com.curio.studio.controller;

import com.curio.studio.port.in.StudioUseCase;
import com.curio.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Self-serve Digest Studio: lets the signed-in user generate their own digest
 * and email it to themselves on demand, polling for live progress. Generation
 * and sending are deliberately separate actions.
 */
@RestController
@RequestMapping("/api/v1/studio")
@RequiredArgsConstructor
@Tag(name = "Digest Studio", description = "User-triggered digest generation and email send")
public class StudioController {

    private final StudioUseCase studioService;

    @PostMapping("/generate")
    @Operation(summary = "Generate today's digest for the current user (async)")
    public ResponseEntity<Map<String, Object>> generate(@AuthenticationPrincipal User user) {
        return ResponseEntity.accepted().body(studioService.startDigestGeneration(user));
    }

    @PostMapping("/send-email")
    @Operation(summary = "Email the current user's latest unsent digest to themselves (async)")
    public ResponseEntity<Map<String, Object>> sendEmail(@AuthenticationPrincipal User user) {
        return ResponseEntity.accepted().body(studioService.startEmailSend(user));
    }

    @GetMapping("/status")
    @Operation(summary = "Live status of the digest/email tasks plus studio context")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(studioService.getStatus(user));
    }
}
