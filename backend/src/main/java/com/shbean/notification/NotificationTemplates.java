package com.shbean.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import com.shbean.credential.Tiers;
import com.shbean.events.Events.CredentialUpdated;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Events.OfferGenerated;
import com.shbean.events.Topics;

/** Customer-facing wording for domain events. Amounts and months only: no account ids, names or other PII. */
final class NotificationTemplates {

    record Message(String type, String title, String body, String link) {
    }

    interface Caps {
        BigDecimal capFor(int tier);
    }

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private NotificationTemplates() {
    }

    static Message offer(OfferGenerated o) {
        BigDecimal due = o.principal().add(o.fee());
        return new Message("OFFER", "New offer: " + usd(o.principal()) + " for " + month(o.shortfallMonth()),
                "Covers your projected shortfall. You'd repay " + usd(due) + " in " + month(o.repaymentMonth())
                        + ", only if your balance stays above the safety buffer.", "/");
    }

    static Optional<Message> loan(String topic, LoanEvent l) {
        BigDecimal due = l.principal().add(l.fee());
        return switch (topic) {
            case Topics.LOAN_DISBURSED -> Optional.of(new Message("LOAN_DISBURSED",
                    l.payee() != null ? "Paid " + l.payee() + " (" + usd(l.principal()) + ")" : usd(l.principal()) + " is in your account",
                    l.payee() != null
                            ? "Your purchase at " + l.payee() + " was paid with a micro-loan. Repay on time to keep your beanstalk growing."
                            : "Your micro-loan was deposited. Your bean is claimed; repay on time to plant it.",
                    "/loans"));
            case Topics.LOAN_REPAID -> Optional.of(new Message("LOAN_REPAID", "Repayment complete",
                    usd(due) + " was repaid automatically. Thanks for keeping your beanstalk growing.", "/loans"));
            case Topics.LOAN_DEFAULTED -> Optional.of(new Message("LOAN_DEFAULTED", "Loan marked as defaulted",
                    "The grace period ended before the " + usd(due) + " repayment could be made. "
                            + "A default removes two leaves from your beanstalk.", "/loans"));
            case Topics.LOAN_FIAT_FAILED -> Optional.of("INSUFFICIENT_FUNDS".equals(l.reason())
                    ? new Message("REPAYMENT_PAUSED", "Repayment paused to protect your balance",
                    "We skipped the " + usd(due) + " draft because it would leave less than your safety buffer. "
                            + "We'll try again next month.", "/loans")
                    : new Message("SETTLEMENT_PROBLEM", "A transfer didn't go through",
                    "Nothing was charged twice. We'll retry, and you don't need to do anything.", "/loans"));
            default -> Optional.empty();
        };
    }

    static Optional<Message> credential(CredentialUpdated c, Caps caps) {
        if (c.tier() > c.previousTier()) {
            return Optional.of(new Message("TIER_UP", "Your beanstalk reached Tier " + c.tier(),
                    "Your micro-loan limit is now " + usd(caps.capFor(c.tier())) + ".", "/beanstalk"));
        }
        if (c.tier() < c.previousTier()) {
            return Optional.of(new Message("TIER_DOWN", "Your tier changed to Tier " + c.tier(),
                    "Your micro-loan limit is now " + usd(caps.capFor(c.tier())) + ".", "/beanstalk"));
        }
        if (c.onTimeRepayments() <= c.previousOnTimeRepayments()) {
            return Optional.empty();
        }
        Integer next = Tiers.nextTierAt(c.tier());
        int effective = Tiers.effectiveOnTime(c.onTimeRepayments(), c.defaults());
        String progress = next == null ? "You're at the highest tier."
                : (next - effective) + " more on-time repayment" + (next - effective == 1 ? "" : "s") + " to Tier "
                + (c.tier() + 1) + ".";
        return Optional.of(new Message("BEANSTALK_GREW", "Your beanstalk grew a leaf", progress, "/beanstalk"));
    }

    private static String usd(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    private static String month(String yearMonth) {
        return YearMonth.parse(yearMonth.length() > 7 ? yearMonth.substring(0, 7) : yearMonth).format(MONTH);
    }
}
