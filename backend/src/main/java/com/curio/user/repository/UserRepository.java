package com.curio.user.repository;

import com.curio.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    boolean existsByEmail(String email);
    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);
    Page<User> findByDeliveryEnabledTrue(Pageable pageable);

    /** Atomic +1 so parallel wrong guesses can't under-count the account budget. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.loginCodeFailures = u.loginCodeFailures + 1 WHERE u.id = :id")
    int incrementLoginCodeFailures(@Param("id") UUID id);

    @Query("SELECT u.loginCodeFailures FROM User u WHERE u.id = :id")
    Optional<Integer> findLoginCodeFailures(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.loginCodeFailures = 0 WHERE u.id = :id")
    int resetLoginCodeFailures(@Param("id") UUID id);
}
