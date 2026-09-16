package com.curio.admin.service;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.shared.scheduler.JobStatusRegistry;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {

    @Mock private UserPort userPort;
    @Mock private DigestPort digestPort;
    @Mock private EmailUseCase emailService;
    @Mock private JobStatusRegistry jobStatusRegistry;

    @InjectMocks private AdminOperationsService service;

    @Test
    void triggerEmailSend_sendsOnlyToUsersWithUnsentDigest_andCountsAll() {
        User withDigest = User.builder().id(UUID.randomUUID()).email("a@example.com").build();
        User withoutDigest = User.builder().id(UUID.randomUUID()).email("b@example.com").build();
        Digest digest = Digest.builder().id(UUID.randomUUID()).build();

        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withDigest, withoutDigest)));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(withDigest.getId()))
                .thenReturn(Optional.of(digest));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(withoutDigest.getId()))
                .thenReturn(Optional.empty());
        when(emailService.sendDigestEmail(any(), any()))
                .thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        Map<String, Object> result = service.triggerEmailSend();

        verify(emailService, times(1)).sendDigestEmail(withDigest, digest);
        verify(emailService, never()).sendDigestEmail(eq(withoutDigest), any());
        assertThat(result.get("sentCount")).isEqualTo(1);
        assertThat(result.get("failCount")).isEqualTo(0);
        assertThat(result.get("totalUsersProcessed")).isEqualTo(2L);
        verify(jobStatusRegistry).recordSuccess(eq("email-send"), any());
    }

    @Test
    void triggerEmailSend_countsFailures_withoutAborting() {
        User u1 = User.builder().id(UUID.randomUUID()).email("a@example.com").build();
        User u2 = User.builder().id(UUID.randomUUID()).email("b@example.com").build();
        Digest d1 = Digest.builder().id(UUID.randomUUID()).build();
        Digest d2 = Digest.builder().id(UUID.randomUUID()).build();

        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u1, u2)));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(any()))
                .thenReturn(Optional.of(d1), Optional.of(d2));
        doThrow(new RuntimeException("resend down")).when(emailService).sendDigestEmail(eq(u1), any());
        when(emailService.sendDigestEmail(eq(u2), any()))
                .thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        Map<String, Object> result = service.triggerEmailSend();

        // u1 fails but the loop continues and still sends u2.
        assertThat(result.get("sentCount")).isEqualTo(1);
        assertThat(result.get("failCount")).isEqualTo(1);
        assertThat(result.get("totalUsersProcessed")).isEqualTo(2L);
    }

    @Test
    void triggerEmailSend_lostClaimIsNotCountedAsSent() {
        User user = User.builder().id(UUID.randomUUID()).email("a@example.com").build();
        Digest digest = Digest.builder().id(UUID.randomUUID()).build();

        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(user.getId()))
                .thenReturn(Optional.of(digest));
        when(emailService.sendDigestEmail(user, digest))
                .thenReturn(EmailUseCase.DigestSendOutcome.ALREADY_CLAIMED);

        Map<String, Object> result = service.triggerEmailSend();

        // Another path owns this digest's send — it is neither our sent nor a failure.
        assertThat(result.get("sentCount")).isEqualTo(0);
        assertThat(result.get("failCount")).isEqualTo(0);
    }
}
