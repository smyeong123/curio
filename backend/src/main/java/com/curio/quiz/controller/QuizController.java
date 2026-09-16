package com.curio.quiz.controller;

import com.curio.quiz.dto.QuizSubmitRequest;
import com.curio.quiz.dto.QuizResponse;
import com.curio.user.entity.User;
import com.curio.quiz.port.in.QuizUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quiz")
@RequiredArgsConstructor
@Tag(name = "Quiz", description = "Quiz endpoints")
public class QuizController {

    private final QuizUseCase quizService;

    @GetMapping("/digest/{digestId}")
    @Operation(summary = "Get quiz for a specific digest")
    public ResponseEntity<QuizResponse> getQuiz(
            @AuthenticationPrincipal User user,
            @PathVariable String digestId) {
        return ResponseEntity.ok(quizService.getQuizForDigest(UUID.fromString(digestId), user.getId()));
    }

    @PostMapping("/{quizId}/submit")
    @Operation(summary = "Submit quiz answers")
    public ResponseEntity<Map<String, Object>> submitQuiz(
            @AuthenticationPrincipal User user,
            @PathVariable String quizId,
            @Valid @RequestBody QuizSubmitRequest request) {
        return ResponseEntity.ok(quizService.submitQuiz(
                UUID.fromString(quizId), user.getId(), request));
    }

    @GetMapping("/history")
    @Operation(summary = "Get quiz attempt history")
    public ResponseEntity<Page<Map<String, Object>>> getHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(quizService.getHistory(user.getId(), page));
    }
}
