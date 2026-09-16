package com.curio.user.port.out;

import com.curio.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for user persistence.
 * Domain services depend on this interface; the JPA adapter (UserRepository)
 * is the infrastructure implementation.
 */
public interface UserPort {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    Page<User> findAll(Pageable pageable);

    /** Batch lookup — used to avoid per-row lazy loads on admin listings. */
    List<User> findAllByIds(Collection<UUID> ids);

    Page<User> findByDeliveryEnabledTrue(Pageable pageable);

    long count();

    User save(User user);

    void delete(User user);

    /** Obtain a managed reference without issuing a SELECT; used for association assignment. */
    User getReferenceById(UUID id);
}
