package com.curio.shared.webhook;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailEventProcessorTest {

    @Mock private DigestPort digestPort;
    @Mock private UserPort userPort;
    @InjectMocks private EmailEventProcessor processor;

    private final User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").deliveryEnabled(true).build();
    private final Digest digest = Digest.builder().id(UUID.randomUUID()).user(owner).emailProviderId("msg_1").build();

    private Map<String, Object> event(String type, Map<String, Object> data) {
        return Map.of("type", type, "data", data);
    }

    @Test
    void anOpen_stampsTheDigestOnce() {
        when(digestPort.findByEmailProviderId("msg_1")).thenReturn(Optional.of(digest));

        processor.processWebhookEvent(event("email.opened", Map.of("email_id", "msg_1")));
        processor.processWebhookEvent(event("email.opened", Map.of("email_id", "msg_1")));

        assertThat(digest.getEmailOpenedAt()).isNotNull();
        verify(digestPort, times(1)).save(digest);
    }

    @Test
    void aHardBounce_turnsTheOwnersDeliveryOff() {
        when(digestPort.findByEmailProviderId("msg_1")).thenReturn(Optional.of(digest));
        when(userPort.findById(owner.getId())).thenReturn(Optional.of(owner));

        processor.processWebhookEvent(event("email.bounced",
                Map.of("email_id", "msg_1", "bounce", Map.of("type", "hard"))));

        assertThat(owner.getDeliveryEnabled()).isFalse();
        verify(userPort).save(owner);
    }

    @Test
    void aSoftBounce_isLeftAlone() {
        when(digestPort.findByEmailProviderId("msg_1")).thenReturn(Optional.of(digest));

        processor.processWebhookEvent(event("email.bounced",
                Map.of("email_id", "msg_1", "bounce", Map.of("type", "soft"))));

        assertThat(owner.getDeliveryEnabled()).isTrue();
        verify(userPort, never()).save(any());
    }

    @Test
    void aComplaint_resolvesTheRecipientByAddress_whenTheDigestIsUnknown() {
        User stranger = User.builder().id(UUID.randomUUID()).email("someone@example.com").deliveryEnabled(true).build();
        when(userPort.findByEmail("someone@example.com")).thenReturn(Optional.of(stranger));

        processor.processWebhookEvent(event("email.complained", Map.of("to", List.of("someone@example.com"))));

        assertThat(stranger.getDeliveryEnabled()).isFalse();
        verify(userPort).save(stranger);
    }
}
