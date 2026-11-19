package com.ganzithon.maeum.concern;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ComfortReplyRepository extends JpaRepository<ComfortReply, Long> {
    boolean existsByConcernIdAndAuthorId(long concernId, long authorId);

    boolean existsByConcernId(long concernId);

    List<ComfortReply> findByConcernIdOrderByCreatedAtAscIdAsc(long concernId);
}
