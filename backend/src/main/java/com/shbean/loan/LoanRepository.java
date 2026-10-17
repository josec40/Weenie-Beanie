package com.shbean.loan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Loan> findFirstByUserIdAndStatusIn(UUID userId, Collection<Loan.Status> statuses);

    Optional<Loan> findByIdAndUserId(UUID id, UUID userId);

    List<Loan> findByStatusIn(Collection<Loan.Status> statuses);
}
