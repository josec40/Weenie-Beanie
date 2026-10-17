package com.shbean.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shbean.ai.ExpenseParser.ExpenseDraft;
import com.shbean.ai.ExpenseParser.RawDraft;
import com.shbean.ai.ExpenseParser.RawDrafts;
import com.shbean.ai.ExpenseParser.Source;

class ExpenseParserTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);
    private final AiGateway ai = mock(AiGateway.class);
    private final ExpenseParser parser = new ExpenseParser(ai);

    @Test
    void rulesParseAmountsDurationsAndCategoriesWhenAiUnavailable() {
        when(ai.entity(anyString(), anyString(), eq(RawDrafts.class))).thenReturn(Optional.empty());

        List<ExpenseDraft> drafts = parser.parse(
                "Netflix for 12 months at $15.49; car loan $385 for 3 years, gym 40 on the 5th", NOW);

        assertThat(drafts).extracting(ExpenseDraft::name, ExpenseDraft::amount, ExpenseDraft::category,
                        ExpenseDraft::durationMonths, ExpenseDraft::dayOfMonth, ExpenseDraft::startMonth, ExpenseDraft::source)
                .containsExactly(
                        tuple("Netflix", new BigDecimal("15.49"), "Entertainment", 12, 1, "2026-11", Source.RULES),
                        tuple("Car loan", new BigDecimal("385.00"), "Loans", 36, 1, "2026-11", Source.RULES),
                        tuple("Gym", new BigDecimal("40.00"), "Health", null, 5, "2026-11", Source.RULES));
    }

    @Test
    void aiDraftsAreSanitizedAndInvalidOnesDropped() {
        when(ai.entity(anyString(), anyString(), eq(RawDrafts.class))).thenReturn(Optional.of(new RawDrafts(List.of(
                new RawDraft("Spotify", 11.99, "subscriptions", 40, "2027-01", 6),
                new RawDraft("Mystery", null, "Other", null, null, null),
                new RawDraft("Huge", 5_000_000.0, "Other", null, null, null),
                new RawDraft("Daycare", 900.0, "not-a-category", null, "garbage", 0)))));

        List<ExpenseDraft> drafts = parser.parse("spotify 11.99 from january for 6 months, daycare 900", NOW);

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0)).isEqualTo(new ExpenseDraft("Spotify", new BigDecimal("11.99"), "Subscriptions", 28, "2027-01", 6, Source.AI));
        assertThat(drafts.get(1)).isEqualTo(new ExpenseDraft("Daycare", new BigDecimal("900.00"), "Childcare", 1, "2026-11", null, Source.AI));
    }

    @Test
    void personalNumbersAndEmailsAreRedactedBeforeReachingTheModel() {
        when(ai.entity(anyString(), anyString(), any())).thenReturn(Optional.empty());

        parser.parse("Pay card 4111 1111 1111 1111 for rent $1450, email me at alex@example.com", NOW);

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(ai).entity(anyString(), user.capture(), eq(RawDrafts.class));
        assertThat(user.getValue()).doesNotContain("4111").doesNotContain("alex@example.com").contains("$1450");
    }

    @Test
    void rejectsEmptyOrOversizedText() {
        assertThatThrownBy(() -> parser.parse(" ", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("x".repeat(501), NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
