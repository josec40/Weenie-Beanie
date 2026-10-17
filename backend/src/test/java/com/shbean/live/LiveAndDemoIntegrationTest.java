package com.shbean.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.client.WebSocketGraphQlClientInterceptor;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.shbean.TestInfra;
import com.shbean.auth.TokenService;
import com.shbean.cashflow.TransactionStore;
import com.shbean.credential.CredentialStore;
import com.shbean.demo.DemoClock;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.OfferGenerated;
import com.shbean.events.Topics;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.Customer;
import com.shbean.nessie.NessieModels.Merchant;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Phase 9: authenticated subscriptions, notifications, demo-only controls and the demo reset. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long!!",
        "nessie.api-key=test",
        "nessie.base-url=http://localhost:1",
        "spring.ai.model.chat=none",
        "nessie.sync.enabled=false",
        "app.demo.clock-enabled=true",
        "app.demo.email=presenter@demo.local"
})
@Import(TestInfra.class)
class LiveAndDemoIntegrationTest {

    @LocalServerPort int port;
    @MockitoBean NessieClient nessie;
    @Autowired AppUserRepository users;
    @Autowired TokenService tokens;
    @Autowired EventPublisher events;
    @Autowired TransactionStore store;
    @Autowired CredentialStore credentials;
    @Autowired DemoClock clock;
    @Autowired JdbcTemplate jdbc;

    @Test
    void notificationsArriveLiveOnlyForTheirOwner() {
        AppUser alice = user("alice");
        AppUser bob = user("bob");
        WebSocketGraphQlTester socket = socket(tokens.issue(alice));

        StepVerifier.create(socket.document("subscription { notificationAdded { title type read } }")
                        .executeSubscription()
                        .toFlux("notificationAdded.title", String.class)
                        .take(1))
                .then(() -> {
                    await().pollDelay(Duration.ofSeconds(1)).until(() -> true);
                    offer(bob);
                    offer(alice);
                })
                .expectNext("New offer: $390.00 for February 2027")
                .expectComplete()
                .verify(Duration.ofSeconds(30));
        socket.stop().block();

        HttpGraphQlTester http = http(tokens.issue(alice));
        await().atMost(Duration.ofSeconds(15)).until(() ->
                http.document("{ unreadNotifications }").execute().path("unreadNotifications").entity(Integer.class)
                        .get() == 1);
        http.document("mutation { markNotificationsRead }").execute().path("markNotificationsRead")
                .entity(Integer.class).isEqualTo(1);
        http.document("{ notifications { read } }").execute().path("notifications[0].read").entity(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    void socketsWithoutAValidTokenAreRefused() {
        StepVerifier.create(socket("not-a-jwt").document("subscription { notificationAdded { title } }")
                        .executeSubscription().toFlux())
                .expectError()
                .verify(Duration.ofSeconds(15));
    }

    @Test
    void demoControlsAreLimitedToTheDemoAccountAndResetReseeds() {
        AppUser someone = user("someone");
        http(tokens.issue(someone)).document("mutation { advanceMonth { offset } }").execute()
                .errors().satisfy(errors -> assertThat(errors).isNotEmpty());
        http(tokens.issue(someone)).document("{ demoStatus { isDemoUser } }").execute()
                .path("demoStatus.isDemoUser").entity(Boolean.class).isEqualTo(false);

        AppUser presenter = users.findByEmail("presenter@demo.local")
                .orElseGet(() -> users.save(new AppUser("presenter@demo.local", "x")));
        presenter.linkNessie("cust-old", "acct-old");
        presenter = users.save(presenter);
        store.insertBalanceSnapshot("acct-old", Instant.now(), presenter.getId(), new BigDecimal("10"));
        jdbc.update("UPDATE demo_clock SET month_offset = 3 WHERE id = 1");
        stubFreshNessieCustomer();

        HttpGraphQlTester http = http(tokens.issue(presenter));
        http.document("mutation { resetDemo { isDemoUser resetState } }").execute()
                .path("resetDemo.isDemoUser").entity(Boolean.class).isEqualTo(true);
        await().atMost(Duration.ofSeconds(60)).until(() -> "DONE".equals(
                http.document("{ demoStatus { resetState } }").execute().path("demoStatus.resetState")
                        .entity(String.class).get()));

        AppUser reset = users.findById(presenter.getId()).orElseThrow();
        assertThat(reset.getNessieCheckingAccountId()).isEqualTo("acct-new");
        assertThat(clock.offset()).isZero();
        assertThat(credentials.find(reset.getId()).orElseThrow().tier()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM balance_snapshot WHERE account_id = 'acct-old'",
                Integer.class)).isZero();
    }

    private void stubFreshNessieCustomer() {
        when(nessie.listMerchants()).thenReturn(List.of());
        when(nessie.createMerchant(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, com.shbean.nessie.NessieModels.CreateMerchantRequest.class);
            return new Merchant("m-" + req.name(), req.name(), List.of(req.category()));
        });
        when(nessie.createCustomer(any())).thenReturn(new Customer("cust-new", "Alex", "Rivera", null));
        when(nessie.createAccount(anyString(), any()))
                .thenReturn(new Account("acct-new", "Checking", "Alex Checking", 0, new BigDecimal("300"), "cust-new"));
        when(nessie.getAccount("acct-new"))
                .thenReturn(new Account("acct-new", "Checking", "Alex Checking", 0, new BigDecimal("300"), "cust-new"));
        when(nessie.listDeposits("acct-new")).thenReturn(List.of());
        when(nessie.listWithdrawals("acct-new")).thenReturn(List.of());
        when(nessie.listPurchases("acct-new")).thenReturn(List.of());
    }

    private void offer(AppUser user) {
        events.publish(Topics.OFFER_GENERATED, user.getId(), new OfferGenerated(UUID.randomUUID(),
                YearMonth.of(2027, 2).toString(), new BigDecimal("390.00"), new BigDecimal("19.50"), "2027-04"));
    }

    private AppUser user(String name) {
        return users.save(new AppUser(name + "-" + UUID.randomUUID() + "@demo.local", "x"));
    }

    private WebSocketGraphQlTester socket(String token) {
        return WebSocketGraphQlTester.builder("ws://localhost:" + port + "/graphql-ws", new ReactorNettyWebSocketClient())
                .interceptor(new WebSocketGraphQlClientInterceptor() {
                    @Override
                    public Mono<Object> connectionInitPayload() {
                        return Mono.just(Map.of("Authorization", "Bearer " + token));
                    }
                })
                .build();
    }

    private HttpGraphQlTester http(String token) {
        return HttpGraphQlTester.builder(WebTestClient.bindToServer()
                        .baseUrl("http://localhost:" + port + "/graphql")
                        .defaultHeader("Authorization", "Bearer " + token)
                        .responseTimeout(Duration.ofSeconds(30)))
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }
}
