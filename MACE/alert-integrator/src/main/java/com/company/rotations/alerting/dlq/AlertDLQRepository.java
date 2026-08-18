package com.company.rotations.alerting.dlq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertDLQRepository extends JpaRepository<AlertDLQEntry, String> {

    List<AlertDLQEntry> findByStatus(AlertDLQEntry.DLQStatus status);

    Optional<AlertDLQEntry> findBySourceAndSourceEventId(String source, String sourceEventId);

    long countByStatus(AlertDLQEntry.DLQStatus status);

    @Modifying
    @Query("DELETE FROM AlertDLQEntry e WHERE e.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    List<AlertDLQEntry> findByStatusInOrderByCreatedAtAsc(List<AlertDLQEntry.DLQStatus> statuses);
}
