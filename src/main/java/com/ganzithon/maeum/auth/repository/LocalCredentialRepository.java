package com.ganzithon.maeum.auth.repository;

import com.ganzithon.maeum.auth.domain.LocalCredential;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {
    Optional<LocalCredential> findByEmail(String normalizedEmail);
    boolean existsByEmail(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LocalCredential c where c.email = :email")
    Optional<LocalCredential> findByEmailForUpdate(@Param("email") String email);
}
