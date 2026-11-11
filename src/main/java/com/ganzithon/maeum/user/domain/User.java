package com.ganzithon.maeum.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String contactEmail;
    @Column(length = 30)
    private String nickname;
    @Column(length = 1000)
    private String profileImageUrl;
    @Column(length = 200)
    private String introduction;
    @Column(length = 30)
    private String ageGroup;
    @Column(length = 30)
    private String regionCode;
    private Instant onboardingCompletedAt;
    private Instant profileCompletedAt;
    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false)
    private Instant updatedAt;

    protected User() { }

    public User(String contactEmail) {
        this.contactEmail = java.util.Objects.requireNonNull(contactEmail);
    }

    public Long getId() { return id; }
    public String getContactEmail() { return contactEmail; }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public String getIntroduction() { return introduction; }
    public String getAgeGroup() { return ageGroup; }
    public String getRegionCode() { return regionCode; }
    public Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isProfileCompleted() { return profileCompletedAt != null; }
    public boolean isOnboardingCompleted() { return onboardingCompletedAt != null; }

    public void updateProfile(String nickname, String introduction, String ageGroup, String regionCode) {
        this.nickname = nickname;
        this.introduction = introduction;
        this.ageGroup = ageGroup;
        this.regionCode = regionCode;
    }

    public void completeProfile(Instant completedAt) {
        if (profileCompletedAt == null) profileCompletedAt = completedAt;
    }

    public void completeOnboarding(Instant completedAt) {
        if (onboardingCompletedAt == null) onboardingCompletedAt = completedAt;
    }
}
