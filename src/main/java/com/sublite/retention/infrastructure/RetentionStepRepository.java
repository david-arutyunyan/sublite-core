package com.sublite.retention.infrastructure;

import com.sublite.retention.domain.RetentionStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetentionStepRepository extends JpaRepository<RetentionStep, UUID> {

    List<RetentionStep> findByActiveTrueOrderByStepOrderAsc();

    Optional<RetentionStep> findFirstByActiveTrueAndStepOrderGreaterThanOrderByStepOrderAsc(int stepOrder);
}
