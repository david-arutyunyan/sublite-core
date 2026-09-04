package com.sublite.retention.infrastructure;

import com.sublite.retention.domain.RetentionOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RetentionOfferRepository extends JpaRepository<RetentionOffer, UUID> {

    Optional<RetentionOffer> findByCode(String code);
}
