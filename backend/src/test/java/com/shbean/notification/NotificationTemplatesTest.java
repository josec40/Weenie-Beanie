package com.shbean.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shbean.events.Events.CredentialUpdated;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Events.OfferGenerated;
import com.shbean.events.Topics;
import com.shbean.notification.NotificationTemplates.Message;

class NotificationTemplatesTest {

    private static final LoanEvent LOAN = new LoanEvent(UUID.randomUUID(), "ACTIVE", new BigDecimal("390.00"),
            new BigDecimal("19.50"), "2027-02", null);

    @Test
    void offerStatesAmountMonthsAndTheSafeguard() {
        Message m = NotificationTemplates.offer(new OfferGenerated(UUID.randomUUID(), "2027-02",
                new BigDecimal("390.00"), new BigDecimal("19.50"), "2027-04"));
        assertThat(m.title()).isEqualTo("New offer: $390.00 for February 2027");
        assertThat(m.body()).contains("$409.50", "April 2027", "safety buffer");
    }

    @Test
    void loanEventsUsePlainLanguage() {
        assertThat(NotificationTemplates.loan(Topics.LOAN_DISBURSED, LOAN)).get()
                .extracting(Message::title).isEqualTo("$390.00 is in your account");
        assertThat(NotificationTemplates.loan(Topics.LOAN_REPAID, LOAN)).get()
                .extracting(Message::body).asString().contains("$409.50");
        LoanEvent paused = new LoanEvent(LOAN.loanId(), "LATE", LOAN.principal(), LOAN.fee(), "2027-04",
                "INSUFFICIENT_FUNDS");
        assertThat(NotificationTemplates.loan(Topics.LOAN_FIAT_FAILED, paused)).get()
                .extracting(Message::type).isEqualTo("REPAYMENT_PAUSED");
        assertThat(NotificationTemplates.loan(Topics.LOAN_ACCEPTED, LOAN)).isEmpty();
    }

    @Test
    void loanDisbursedWithPayeeShowsVendorPaid() {
        LoanEvent purchaseLoan = new LoanEvent(LOAN.loanId(), "ACTIVE", LOAN.principal(), LOAN.fee(), "2027-02", null, "Bean Mart");
        Message m = NotificationTemplates.loan(Topics.LOAN_DISBURSED, purchaseLoan).orElseThrow();
        assertThat(m.title()).isEqualTo("Paid Bean Mart ($390.00)");
        assertThat(m.body()).contains("Your purchase at Bean Mart was paid with a micro-loan");
    }

    @Test
    void credentialOnlyNotifiesOnGrowthOrTierChange() {
        NotificationTemplates.Caps caps = tier -> new BigDecimal(new int[] {200, 500, 1000}[tier]);

        assertThat(NotificationTemplates.credential(new CredentialUpdated(1, 0, 2, 1, 0, 0, true), caps)).get()
                .extracting(Message::title).isEqualTo("Your beanstalk reached Tier 1");
        assertThat(NotificationTemplates.credential(new CredentialUpdated(1, 1, 3, 2, 0, 0, true), caps)).get()
                .extracting(Message::body).isEqualTo("2 more on-time repayments to Tier 2.");
        assertThat(NotificationTemplates.credential(new CredentialUpdated(1, 1, 3, 3, 1, 0, true), caps)).isEmpty();
    }

    @Test
    void noAccountIdentifiersLeakIntoText() {
        Message m = NotificationTemplates.loan(Topics.LOAN_DEFAULTED, LOAN).orElseThrow();
        assertThat(m.title() + m.body()).doesNotContain(LOAN.loanId().toString());
    }
}
