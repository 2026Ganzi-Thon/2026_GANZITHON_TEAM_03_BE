package com.ganzithon.maeum.auth.repository;

import com.ganzithon.maeum.auth.domain.LocalCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {
    Optional<LocalCredential> findByEmail(String normalizedEmail);
    boolean existsByEmail(String normalizedEmail);
}
