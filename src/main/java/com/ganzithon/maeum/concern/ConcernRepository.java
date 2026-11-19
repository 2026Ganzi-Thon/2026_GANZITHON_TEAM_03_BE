package com.ganzithon.maeum.concern;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 고민 저장소입니다. 홈 화면의 고민 수 조회 때문에 공개합니다. */
public interface ConcernRepository extends JpaRepository<Concern, Long> {
    /** 보관함 목록의 한 줄입니다. */
    record SavedRow(Concern concern, long replyCount) {}

    /** 개봉·보관·보관 취소용입니다. 내 고민 행을 FOR UPDATE로 잠가 동시 요청을 직렬화합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Concern c where c.id = :id and c.userId = :user")
    Optional<Concern> findMineForUpdate(@Param("id") long id, @Param("user") long user);

    /** 위로 작성용입니다. FOR SHARE로 잠가 위로 저장이 끝날 때까지 개봉·삭제가 기다리게 합니다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Concern c where c.id = :id")
    Optional<Concern> findForShare(@Param("id") long id);

    Optional<Concern> findByIdAndUserId(long id, long userId);

    /** 위로할 수 있는 남의 고민을 무작위로 고릅니다. */
    @Query(
            value =
                    "SELECT c.* FROM concerns c WHERE c.user_id <> :user AND c.matures_at > :now"
                        + " AND NOT EXISTS (SELECT 1 FROM comfort_replies r WHERE r.concern_id ="
                        + " c.id AND r.author_id = :user) ORDER BY RAND() LIMIT :limit",
            nativeQuery = true)
    List<Concern> findReplyCandidates(
            @Param("user") long user, @Param("now") Instant now, @Param("limit") int limit);

    Slice<Concern> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Pageable pageable);

    @Query(
            "select new com.ganzithon.maeum.concern.ConcernRepository$SavedRow(c, (select count(r)"
                    + " from ComfortReply r where r.concernId = c.id)) from Concern c where c.userId"
                    + " = :user and c.savedAt is not null order by c.savedAt desc, c.id desc")
    Slice<SavedRow> findSaved(@Param("user") long user, Pageable pageable);

    @Modifying
    @Query("delete from Concern c where c.id = :id and c.userId = :user")
    int deleteMine(@Param("id") long id, @Param("user") long user);

    /** 숙성됐지만 아직 열지 않은 내 고민 수입니다. */
    @Query(
            "select count(c) from Concern c where c.userId = :user and c.maturesAt <= :now and"
                    + " c.openedAt is null")
    long countUnopenedMatured(@Param("user") long user, @Param("now") Instant now);
}
