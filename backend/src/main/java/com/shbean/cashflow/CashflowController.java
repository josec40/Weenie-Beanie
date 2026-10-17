package com.shbean.cashflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.cashflow.RecurringExpenseService.Input;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

@Controller
public class CashflowController {

    public record TransactionView(String id, LocalDate date, String kind, BigDecimal amount, String category,
                                  String description) {
    }

    private static final long MAX_RANGE_DAYS = 1826;

    private final TransactionStore store;
    private final RecurringExpenseService recurring;
    private final AppUserRepository users;

    public CashflowController(TransactionStore store, RecurringExpenseService recurring, AppUserRepository users) {
        this.store = store;
        this.recurring = recurring;
        this.users = users;
    }

    @QueryMapping
    public List<TransactionView> transactions(@Argument String from, @Argument String to, @AuthenticationPrincipal Jwt jwt) {
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(from);
            end = LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("from/to must be YYYY-MM-DD");
        }
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("range must be non-negative and at most 2 years");
        }
        return store.transactions(userId(jwt), start, end.plusDays(1)).stream()
                .map(t -> new TransactionView(t.id(), t.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate(), t.kind(),
                        t.amount(), t.category(), t.description()))
                .toList();
    }

    @QueryMapping
    public List<RecurringExpense> recurringExpenses(@AuthenticationPrincipal Jwt jwt) {
        return recurring.list(userId(jwt));
    }

    @MutationMapping
    public RecurringExpense createRecurringExpense(@Argument Input input, @AuthenticationPrincipal Jwt jwt) {
        return recurring.create(user(jwt), input);
    }

    @MutationMapping
    public RecurringExpense updateRecurringExpense(@Argument UUID id, @Argument Input input, @AuthenticationPrincipal Jwt jwt) {
        return recurring.update(userId(jwt), id, input).orElse(null);
    }

    @MutationMapping
    public boolean deleteRecurringExpense(@Argument UUID id, @AuthenticationPrincipal Jwt jwt) {
        return recurring.delete(userId(jwt), id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private AppUser user(Jwt jwt) {
        return users.findById(userId(jwt)).orElseThrow(() -> new IllegalArgumentException("unknown user"));
    }
}
