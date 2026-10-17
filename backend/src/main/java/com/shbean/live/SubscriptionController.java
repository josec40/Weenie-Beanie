package com.shbean.live;

import java.util.UUID;

import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import com.shbean.auth.WebSocketAuthInterceptor;
import com.shbean.credential.CredentialController.CredentialView;
import com.shbean.live.LiveEvents.Kind;
import com.shbean.live.LiveEventsBridge.Balance;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanOffer;
import com.shbean.notification.NotificationService.Notification;

import reactor.core.publisher.Flux;

/** Per-user subscriptions; the user id comes from the authenticated WebSocket session, never from arguments. */
@Controller
public class SubscriptionController {

    private final LiveEvents live;

    public SubscriptionController(LiveEvents live) {
        this.live = live;
    }

    @SubscriptionMapping
    public Flux<Loan> loanUpdated(@ContextValue(name = WebSocketAuthInterceptor.USER_ID) UUID userId) {
        return live.stream(userId, Kind.LOAN, Loan.class);
    }

    @SubscriptionMapping
    public Flux<LoanOffer> offerCreated(@ContextValue(name = WebSocketAuthInterceptor.USER_ID) UUID userId) {
        return live.stream(userId, Kind.OFFER, LoanOffer.class);
    }

    @SubscriptionMapping
    public Flux<CredentialView> credentialUpdated(@ContextValue(name = WebSocketAuthInterceptor.USER_ID) UUID userId) {
        return live.stream(userId, Kind.CREDENTIAL, CredentialView.class);
    }

    @SubscriptionMapping
    public Flux<Balance> balanceUpdated(@ContextValue(name = WebSocketAuthInterceptor.USER_ID) UUID userId) {
        return live.stream(userId, Kind.BALANCE, Balance.class);
    }

    @SubscriptionMapping
    public Flux<Notification> notificationAdded(@ContextValue(name = WebSocketAuthInterceptor.USER_ID) UUID userId) {
        return live.stream(userId, Kind.NOTIFICATION, Notification.class);
    }
}
