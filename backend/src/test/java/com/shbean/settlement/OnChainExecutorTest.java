package com.shbean.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.shbean.settlement.OnChainExecutor.Plan;
import com.shbean.settlement.SettlementSteps.State;
import com.shbean.settlement.SettlementSteps.Step;
import com.shbean.solana.Keypair;
import com.shbean.solana.SolanaClient;
import com.shbean.solana.SolanaClient.Instruction;
import com.shbean.solana.SolanaClient.SignedTransaction;
import com.shbean.solana.SolanaClient.Status;
import com.shbean.solana.SolanaClient.TxResult;
import com.shbean.solana.SolanaException;

class OnChainExecutorTest {

    private static final UUID LOAN = UUID.randomUUID();
    private static final String STEP = "DISBURSE";
    private static final Keypair PAYER = Keypair.generate();

    private final SolanaClient client = mock(SolanaClient.class);
    private final SettlementSteps steps = mock(SettlementSteps.class);
    private final OnChainExecutor executor = new OnChainExecutor(client, steps, Duration.ofSeconds(1),
            Duration.ofMillis(200), Duration.ofMillis(1));
    private final AtomicInteger plansBuilt = new AtomicInteger();

    @Test
    void recordsTheSignatureBeforeSubmittingAndMarksTheStepDone() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.empty());
        signs("sig-1", "bh-1");
        when(client.awaitConfirmation(eq("sig-1"), any())).thenReturn(new TxResult("sig-1", Status.CONFIRMED, null));

        assertThat(run(false)).isEqualTo("sig-1");

        InOrder order = inOrder(steps, client);
        order.verify(steps).pending(LOAN, STEP, "sig-1", "bh-1");
        order.verify(client).submit(any());
        order.verify(steps).done(LOAN, STEP, "sig-1", null);
    }

    @Test
    void doneStepIsNotRepeated() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.of(new Step(State.DONE, "sig-0", null, null, null)));

        assertThat(run(false)).isEqualTo("sig-0");
        assertThat(plansBuilt).hasValue(0);
        verify(client, never()).sign(anyList(), any(), anyList());
    }

    @Test
    void programStateShowingTheEffectSkipsTheTransaction() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.empty());

        assertThat(run(true)).isNull();
        assertThat(plansBuilt).hasValue(0);
        verify(steps).done(LOAN, STEP, null, null);
    }

    @Test
    void pendingSignatureThatLandedIsAdoptedAfterACrash() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.of(new Step(State.PENDING, "sig-0", "bh-0", null, null)));
        when(client.status("sig-0")).thenReturn(new TxResult("sig-0", Status.FINALIZED, null));

        assertThat(run(false)).isEqualTo("sig-0");
        verify(steps).done(LOAN, STEP, "sig-0", null);
        verify(client, never()).sign(anyList(), any(), anyList());
    }

    @Test
    void unseenTransactionIsReplacedOnlyAfterItsBlockhashExpired() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.of(new Step(State.PENDING, "sig-0", "bh-0", null, null)));
        when(client.status("sig-0")).thenReturn(new TxResult("sig-0", Status.NOT_FOUND, null));
        when(client.isBlockhashValid("bh-0")).thenReturn(true, true, false);
        signs("sig-1", "bh-1");
        when(client.awaitConfirmation(eq("sig-1"), any())).thenReturn(new TxResult("sig-1", Status.CONFIRMED, null));

        assertThat(run(false)).isEqualTo("sig-1");

        InOrder order = inOrder(steps, client);
        order.verify(steps).failed(LOAN, STEP, "expired before landing");
        order.verify(client).sign(anyList(), eq(PAYER), anyList());
        order.verify(steps).pending(LOAN, STEP, "sig-1", "bh-1");
    }

    @Test
    void transactionThatMayStillLandBlocksANewOne() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.of(new Step(State.PENDING, "sig-0", "bh-0", null, null)));
        when(client.status("sig-0")).thenReturn(new TxResult("sig-0", Status.NOT_FOUND, null));
        when(client.isBlockhashValid("bh-0")).thenReturn(true);

        assertThatThrownBy(() -> run(false)).isInstanceOf(SettlementException.class).hasMessageContaining("still pending");
        verify(client, never()).sign(anyList(), any(), anyList());
    }

    @Test
    void failedOnChainIsRecordedAndRetried() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.empty());
        signs("sig-1", "bh-1");
        when(client.awaitConfirmation(eq("sig-1"), any()))
                .thenReturn(new TxResult("sig-1", Status.FAILED, "InsufficientPoolFunds"));

        assertThatThrownBy(() -> run(false)).isInstanceOf(SettlementException.class)
                .hasMessageContaining("InsufficientPoolFunds");
        verify(steps).failed(LOAN, STEP, "InsufficientPoolFunds");
    }

    @Test
    void rejectedSubmissionStaysPendingUntilResolved() {
        when(steps.find(LOAN, STEP)).thenReturn(Optional.empty());
        signs("sig-1", "bh-1");
        doThrow(new SolanaException("preflight failed")).when(client).submit(any());

        assertThatThrownBy(() -> run(false)).isInstanceOf(SettlementException.class).hasMessageContaining("not accepted");
        verify(steps).pending(LOAN, STEP, "sig-1", "bh-1");
        verify(steps, never()).failed(any(), anyString(), any());
    }

    private String run(boolean alreadyDone) {
        return executor.execute(LOAN, STEP, () -> alreadyDone, () -> {
            plansBuilt.incrementAndGet();
            return new Plan(List.of(new Instruction("Program", List.of(), new byte[0])), PAYER, List.of());
        });
    }

    private void signs(String signature, String blockhash) {
        when(client.sign(anyList(), eq(PAYER), anyList()))
                .thenReturn(new SignedTransaction(signature, blockhash, new byte[0]));
    }
}
