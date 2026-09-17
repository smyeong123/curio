package com.curio.user.repository;

import com.curio.user.entity.UserApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserApiKeyRepository extends JpaRepository<UserApiKey, UUID> {

    Optional<UserApiKey> findByUserIdAndProvider(UUID userId, UserApiKey.Provider provider);

    List<UserApiKey> findByUserId(UUID userId);

    void deleteByUserIdAndProvider(UUID userId, UserApiKey.Provider provider);
}
