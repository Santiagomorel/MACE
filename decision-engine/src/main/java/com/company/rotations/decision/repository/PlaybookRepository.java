package com.company.rotations.decision.repository;

import com.company.rotations.models.PlaybookStandard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaybookRepository extends JpaRepository<PlaybookStandard, UUID> {
    Optional<PlaybookStandard> findByPlaybookId(String playbookId);
    Optional<PlaybookStandard> findTopByOrderByUpdatedAtDesc();
}
