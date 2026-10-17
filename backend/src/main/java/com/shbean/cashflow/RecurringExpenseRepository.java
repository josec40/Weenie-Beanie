package com.shbean.cashflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    List<RecurringExpense> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<RecurringExpense> findByIdAndUserId(UUID id, UUID userId);
}
