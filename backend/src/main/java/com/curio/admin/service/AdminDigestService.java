package com.curio.admin.service;

import com.curio.admin.dto.AdminDigestResponse;
import com.curio.admin.port.in.AdminDigestUseCase;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.digest.DigestBatch;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDigestService implements AdminDigestUseCase {

    private final DigestPort digestPort;
    private final UserPort userPort;
    /** The same batch the 06:00 job runs; the admin trigger may narrow it by topic. */
    private final DigestBatch digestBatch;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminDigestResponse> getDigests(int page, int size, String topic, String userEmail) {
        // All underlying finders already sort by generated_at DESC in their SQL
        // (either via method name or the @Query ORDER BY). Adding a Sort here
        // would get appended to native queries using JPA property names — which
        // don't translate for native SQL and blow up at runtime.
        Pageable pageable = PageRequest.of(page, size);

        Page<Digest> digests;
        boolean hasTopic = topic != null && !topic.isBlank();
        boolean hasEmail = userEmail != null && !userEmail.isBlank();

        if (hasEmail) {
            UUID userId = userPort.findByEmail(userEmail.trim())
                    .map(User::getId)
                    .orElse(null);
            if (userId == null) {
                return Page.empty(pageable);
            }
            digests = hasTopic
                    ? digestPort.findByUserIdAndTopicInContent(userId, topic.trim(), pageable)
                    : digestPort.findByUserIdOrderByGeneratedAtDesc(userId, pageable);
        } else if (hasTopic) {
            digests = digestPort.findByTopicInContent(topic.trim(), pageable);
        } else {
            digests = digestPort.findAllByOrderByGeneratedAtDesc(pageable);
        }

        // Digest.user is LAZY; reading email/fullName per row would fire one
        // SELECT per distinct user in the page (N+1 on broad listings). Accessing
        // the proxy's id is free, so batch-load the owners in one query instead.
        Map<UUID, User> ownersById = new HashMap<>();
        List<UUID> ownerIds = digests.getContent().stream()
                .map(d -> d.getUser().getId())
                .distinct()
                .toList();
        if (!ownerIds.isEmpty()) {
            userPort.findAllByIds(ownerIds).forEach(u -> ownersById.put(u.getId(), u));
        }

        return digests.map(digest -> {
            User owner = ownersById.getOrDefault(digest.getUser().getId(), digest.getUser());
            return AdminDigestResponse.builder()
                    .id(digest.getId())
                    .userId(owner.getId())
                    .userEmail(owner.getEmail())
                    .userFullName(owner.getFullName())
                    .content(digest.getContent())
                    .generatedAt(digest.getGeneratedAt())
                    .emailSentAt(digest.getEmailSentAt())
                    .build();
        });
    }

    @Override
    public Map<String, Object> triggerDigestGeneration() {
        return digestBatch.runForAll();
    }

    @Override
    public Map<String, Object> triggerDigestGeneration(List<String> topicFilter) {
        return digestBatch.run(topicFilter);
    }
}
