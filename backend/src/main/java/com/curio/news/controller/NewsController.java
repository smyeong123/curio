package com.curio.news.controller;

import com.curio.news.dto.DigestResponse;
import com.curio.user.entity.User;
import com.curio.news.port.in.NewsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Tag(name = "News", description = "News digest endpoints")
public class NewsController {

    private final NewsUseCase newsService;

    @GetMapping("/digests")
    @Operation(summary = "Get user's news digests (paginated)")
    public ResponseEntity<Page<DigestResponse>> getDigests(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return ResponseEntity.ok(newsService.getDigests(user.getId(), page, size));
    }

    @GetMapping("/digests/{id}")
    @Operation(summary = "Get a single digest by ID")
    public ResponseEntity<DigestResponse> getDigest(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        return ResponseEntity.ok(newsService.getDigest(
                java.util.UUID.fromString(id), user.getId()));
    }

    @GetMapping("/search")
    @Operation(summary = "Full-text search over the user's own digest archive")
    public ResponseEntity<Page<DigestResponse>> search(
            @AuthenticationPrincipal User user,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return ResponseEntity.ok(newsService.searchDigests(user.getId(), query, page, size));
    }
}
