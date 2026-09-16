package com.curio.shared.email;

import com.curio.auth.service.UnsubscribeTokenService;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.entity.Quiz;
import com.curio.quiz.port.out.QuizPort;
import com.curio.shared.i18n.Language;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Renders the REAL digest template through the REAL message catalogs (no Spring
 * context), so a missing key, a broken {@code #{...}} expression or a catalog that
 * drifts between editions fails here instead of in the 08:00 send.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceRenderTest {

    @Mock private DigestPort digestPort;
    @Mock private UserPort userPort;
    @Mock private QuizPort quizPort;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private UnsubscribeTokenService unsubscribeTokenService;

    private EmailService emailService;

    private static final Map<String, Object> STORY = Map.of(
            "headline", "Anthropic turns on long-horizon tool use",
            "summary", "What changed in the API.",
            "why_it_matters", "Agents that run for hours change budgets.",
            "source_url", "https://example.com/story",
            "source_name", "Anthropic blog",
            "topic", "Claude (Anthropic)");

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        templateEngine.setMessageSource(messageSource);

        emailService = new EmailService(digestPort, userPort, quizPort, templateEngine, new ObjectMapper(),
                restTemplateBuilder, unsubscribeTokenService, messageSource);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://curio.test");
        ReflectionTestUtils.setField(emailService, "backendUrl", "https://api.curio.test");

        lenient().when(unsubscribeTokenService.generateToken(any())).thenReturn("unsub-token");
        lenient().when(quizPort.findByDigestId(any())).thenReturn(Optional.of(Quiz.builder().id(UUID.randomUUID()).build()));
    }

    private Digest digest(Map<String, Object> content) {
        return Digest.builder().id(UUID.randomUUID()).content(content).build();
    }

    private User user(String name) {
        return User.builder().id(UUID.randomUUID()).email("reader@example.com").fullName(name).build();
    }

    @Test
    void rendersTheKoreanEdition_whenTheDigestWasWrittenInKorean() {
        String html = emailService.renderDigestEmail(user("Alex"),
                digest(Map.of("language", "ko", "summaries", List.of(STORY), "generatedFor", List.of("Claude (Anthropic)"))));
        System.out.println("HTML>>>" + html.substring(0, Math.min(1400, html.length())) + "<<<HTML");

        assertThat(html).contains("lang=\"ko\"");
        assertThat(html).contains("Alex 님, 좋은 아침이에요");
        assertThat(html).contains("오늘의 브리핑").contains("왜 중요한가").contains("기사 전문 읽기");
        assertThat(html).contains("퀴즈 풀기").contains("구독 해지").contains("AI 모델 뉴스 일간지");
        // Story content and the canonical topic name are data, never translated by the template.
        assertThat(html).contains("Anthropic turns on long-horizon tool use").contains("Claude (Anthropic)");
        assertThat(html).doesNotContain("Why it matters").doesNotContain("Take the quiz").doesNotContain("Good morning");
    }

    @Test
    void rendersTheEnglishEdition_forLegacyDigestsWithoutALanguage() {
        String html = emailService.renderDigestEmail(user("Alex"),
                digest(Map.of("summaries", List.of(STORY), "generatedFor", List.of("Claude (Anthropic)"))));

        assertThat(html).contains("lang=\"en\"");
        // th:text escapes the apostrophe — that is correct for HTML mail.
        assertThat(html).contains("Good morning, Alex. Here&#39;s what moved in AI today.");
        assertThat(html).contains("Why it matters").contains("Read full story").contains("Take the quiz").contains("Unsubscribe");
        assertThat(html).doesNotContain("왜 중요한가");
    }

    @Test
    void fallsBackToAGenericReader_whenTheUserHasNoName() {
        String ko = emailService.renderDigestEmail(user(null),
                digest(Map.of("language", "ko", "summaries", List.of(STORY))));
        String en = emailService.renderDigestEmail(user(" "),
                digest(Map.of("summaries", List.of(STORY))));

        assertThat(ko).contains("독자 님, 좋은 아침이에요");
        assertThat(en).contains("Good morning, reader.");
    }

    @Test
    void omitsTheQuizBlock_whenNoQuizExists() {
        when(quizPort.findByDigestId(any())).thenReturn(Optional.empty());

        String html = emailService.renderDigestEmail(user("Alex"),
                digest(Map.of("language", "ko", "summaries", List.of(STORY))));

        assertThat(html).doesNotContain("퀴즈 풀기").doesNotContain("기억력 테스트");
    }

    @Test
    void subjectLine_followsTheDigestLanguage() {
        assertThat(emailService.digestSubject(Language.KO))
                .matches("Curio 데일리 다이제스트 — \\d{4}년 \\d{1,2}월 \\d{1,2}일");
        assertThat(emailService.digestSubject(Language.EN))
                .matches("Your Curio Daily Digest — [A-Z][a-z]+ \\d{1,2}, \\d{4}");
    }
}
