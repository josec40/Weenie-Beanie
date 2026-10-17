package com.shbean.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shbean.loan.OfferEngine.Proposal;

class LoanTest {

    private static final YearMonth FEB = YearMonth.of(2027, 2);
    private static final YearMonth APR = YearMonth.of(2027, 4);

    private static Loan newLoan() {
        return new Loan(new LoanOffer(UUID.randomUUID(), null,
                new Proposal(FEB, new BigDecimal("342"), new BigDecimal("342"), new BigDecimal("17.10"), APR, new BigDecimal("1352"), 1),
                "text"));
    }

    @Test
    void happyPathLifecycle() {
        Loan l = newLoan();
        assertThat(l.getStatus()).isEqualTo(Loan.Status.ACCEPTED);
        assertThat(l.getTotalDue()).isEqualByComparingTo("359.10");

        l.startDisbursing();
        l.disbursed("dep-1");
        assertThat(l.getStatus()).isEqualTo(Loan.Status.ACTIVE);
        l.startRepaying();
        l.repaid("wd-1");
        assertThat(l.getStatus()).isEqualTo(Loan.Status.REPAID);
        assertThat(l.getNessieWithdrawalId()).isEqualTo("wd-1");
    }

    @Test
    void lateKeepsFirstGraceWindowThenDefaults() {
        Loan l = newLoan();
        l.startDisbursing();
        l.disbursed("dep-1");
        l.startRepaying();
        l.late("INSUFFICIENT_FUNDS", APR.plusMonths(1));
        l.startRepaying();
        l.late("INSUFFICIENT_FUNDS", APR.plusMonths(2));

        assertThat(l.getStatus()).isEqualTo(Loan.Status.LATE);
        assertThat(l.getGraceUntilMonth()).isEqualTo(APR.plusMonths(1));
        l.defaulted();
        assertThat(l.getStatus()).isEqualTo(Loan.Status.DEFAULTED);
    }

    @Test
    void illegalTransitionsAreRejected() {
        Loan l = newLoan();
        assertThatThrownBy(() -> l.disbursed("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(l::startRepaying).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(l::defaulted).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> l.repayIn(FEB)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rescheduleOnceWithinWindow() {
        Loan l = newLoan();
        l.reschedule(FEB.plusMonths(3));
        assertThat(l.getRepayMonth()).isEqualTo(APR.plusMonths(1));
        assertThatThrownBy(() -> l.reschedule(FEB.plusMonths(3)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only once");

        Loan other = newLoan();
        assertThatThrownBy(() -> other.reschedule(APR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onTimeMeansTheOriginalMonthAndNeverLate() {
        Loan scheduled = repaid(newLoan(), null);
        assertThat(scheduled.isOnTime()).isTrue();

        Loan early = newLoan();
        early.startDisbursing();
        early.disbursed("dep");
        early.repayIn(FEB.plusMonths(1));
        assertThat(repaid(early, null).isOnTime()).isTrue();

        Loan rescheduled = newLoan();
        rescheduled.reschedule(FEB.plusMonths(3));
        assertThat(repaid(rescheduled, null).isOnTime()).isFalse();
        assertThat(rescheduled.getOriginalRepayMonth()).isEqualTo(APR);

        Loan wentLate = newLoan();
        assertThat(repaid(wentLate, "INSUFFICIENT_FUNDS").isOnTime()).isFalse();

        Loan open = newLoan();
        assertThat(open.isOnTime()).isFalse();
    }

    private static Loan repaid(Loan l, String lateFirst) {
        if (l.getStatus() == Loan.Status.ACCEPTED) {
            l.startDisbursing();
            l.disbursed("dep");
        }
        l.startRepaying();
        if (lateFirst != null) {
            l.late(lateFirst, APR.plusMonths(1));
            l.startRepaying();
        }
        l.repaid("wd");
        return l;
    }
}
