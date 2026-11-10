package com.ganzithon.maeum.auth.domain;

import com.ganzithon.maeum.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "local_credentials")
public class LocalCredential {
    @Id
    private Long userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 255)
    private String email;
    @Column(nullable = false, length = 255)
    private String passwordHash;
    private Instant emailVerifiedAt;
    @Column(nullable = false)
    private Instant passwordChangedAt;
    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false)
    private Instant updatedAt;

    protected LocalCredential() { }

    public LocalCredential(User user, EmailAddress email, String passwordHash) {
        this.user = java.util.Objects.requireNonNull(user);
        this.email = java.util.Objects.requireNonNull(email.value());
        this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
        this.passwordChangedAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
}
