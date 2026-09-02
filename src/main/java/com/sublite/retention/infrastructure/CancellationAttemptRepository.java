package com.sublite.retention.infrastructure;

import com.sublite.retention.domain.CancellationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CancellationAttemptRepository extends JpaRepository<CancellationAttempt, UUID> {
}
