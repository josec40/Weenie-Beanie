package com.shbean.cashflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.Topics;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieException;
import com.shbean.nessie.NessieModels.BillRequest;
import com.shbean.user.AppUser;

/** CRUD for user-entered recurring expenses; the local row is the source of truth, mirrored to a Nessie bill. */
@Service
public class RecurringExpenseService {

    public record Input(String name, BigDecimal amount, String category, int dayOfMonth, String startMonth,
                        Integer durationMonths) {
    }

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000");

    private final RecurringExpenseRepository repo;
    private final NessieClient nessie;
    private final EventPublisher events;

    public RecurringExpenseService(RecurringExpenseRepository repo, NessieClient nessie, EventPublisher events) {
        this.repo = repo;
        this.nessie = nessie;
        this.events = events;
    }

    public List<RecurringExpense> list(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public RecurringExpense create(AppUser user, Input input) {
        RecurringExpense expense = new RecurringExpense(user.getId());
        apply(expense, input);
        if (user.getNessieCheckingAccountId() != null) {
            try {
                expense.setNessieBillId(nessie.createBill(user.getNessieCheckingAccountId(), billOf(expense)).id());
            } catch (NessieException e) {
                log.warn("Could not mirror recurring expense {} to Nessie: {}", expense.getId(), e.getMessage());
            }
        }
        RecurringExpense saved = repo.save(expense);
        changed(user.getId());
        return saved;
    }

    public Optional<RecurringExpense> update(UUID userId, UUID id, Input input) {
        return repo.findByIdAndUserId(id, userId).map(expense -> {
            apply(expense, input);
            if (expense.getNessieBillId() != null) {
                try {
                    nessie.updateBill(expense.getNessieBillId(), billOf(expense));
                } catch (NessieException e) {
                    log.warn("Could not update Nessie bill for recurring expense {}: {}", id, e.getMessage());
                }
            }
            RecurringExpense saved = repo.save(expense);
            changed(userId);
            return saved;
        });
    }

    public boolean delete(UUID userId, UUID id) {
        return repo.findByIdAndUserId(id, userId).map(expense -> {
            if (expense.getNessieBillId() != null) {
                try {
                    nessie.deleteBill(expense.getNessieBillId());
                } catch (NessieException e) {
                    log.warn("Could not delete Nessie bill for recurring expense {}: {}", id, e.getMessage());
                }
            }
            repo.delete(expense);
            changed(userId);
            return true;
        }).orElse(false);
    }

    private void changed(UUID userId) {
        events.publish(Topics.CASHFLOW_CHANGED, userId, new CashflowChanged(CashflowReason.RECURRING_EXPENSE));
    }

    private static void apply(RecurringExpense expense, Input in) {
        String name = in.name() == null ? "" : in.name().strip();
        String category = in.category() == null ? "" : in.category().strip();
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalArgumentException("name must be 1-120 characters");
        }
        if (category.isEmpty() || category.length() > 64) {
            throw new IllegalArgumentException("category must be 1-64 characters");
        }
        if (in.amount() == null || in.amount().signum() <= 0 || in.amount().compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amount must be between 0 and 100000");
        }
        if (in.dayOfMonth() < 1 || in.dayOfMonth() > 28) {
            throw new IllegalArgumentException("dayOfMonth must be between 1 and 28");
        }
        if (in.durationMonths() != null && (in.durationMonths() < 1 || in.durationMonths() > 600)) {
            throw new IllegalArgumentException("durationMonths must be between 1 and 600");
        }
        YearMonth start;
        try {
            start = YearMonth.parse(in.startMonth());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("startMonth must be YYYY-MM");
        }
        expense.update(name, in.amount().setScale(2, RoundingMode.HALF_UP), category, in.dayOfMonth(),
                start, in.durationMonths());
    }

    private static BillRequest billOf(RecurringExpense e) {
        return new BillRequest("recurring", e.getName(), e.getName(), e.getStartMonth().atDay(e.getDayOfMonth()),
                e.getDayOfMonth(), e.getAmount());
    }
}
