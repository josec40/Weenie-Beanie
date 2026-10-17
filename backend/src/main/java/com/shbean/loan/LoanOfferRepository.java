package com.shbean.loan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanOfferRepository extends JpaRepository<LoanOffer, UUID> {

    Optional<LoanOffer> findFirstByUserIdAndStatus(UUID userId, LoanOffer.Status status);

    Optional<LoanOffer> findByIdAndUserId(UUID id, UUID userId);

    List<LoanOffer> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
