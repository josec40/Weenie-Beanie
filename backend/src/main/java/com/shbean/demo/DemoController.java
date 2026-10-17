package com.shbean.demo;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.credential.ReputationLedger;
import com.shbean.demo.DemoResetService.ResetStatus;
import com.shbean.demo.DemoService.ClockView;
import com.shbean.settlement.SolanaBank;
import com.shbean.solana.SolanaProperties;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

@Controller
public class DemoController {

    public record DemoStatus(ClockView clock, boolean isDemoUser, String resetState, String resetMessage,
                             String bankAddress, String bankUrl, String poolUrl, String reputationConfigUrl) {
    }

    private final DemoService demo;
    private final DemoResetService reset;
    private final AppUserRepository users;
    private final SolanaProperties solana;
    private final ObjectProvider<SolanaBank> bank;
    private final ObjectProvider<ReputationLedger> reputation;

    public DemoController(DemoService demo, DemoResetService reset, AppUserRepository users, SolanaProperties solana,
                          ObjectProvider<SolanaBank> bank, ObjectProvider<ReputationLedger> reputation) {
        this.demo = demo;
        this.reset = reset;
        this.users = users;
        this.solana = solana;
        this.bank = bank;
        this.reputation = reputation;
    }

    @QueryMapping
    public DemoStatus demoStatus(@AuthenticationPrincipal Jwt jwt) {
        return status(user(jwt));
    }

    @MutationMapping
    public DemoStatus resetDemo(@AuthenticationPrincipal Jwt jwt) {
        AppUser caller = user(jwt);
        demo.requireDemoUser(caller);
        reset.start(caller);
        return status(caller);
    }

    private DemoStatus status(AppUser caller) {
        boolean isDemoUser = demo.enabled() && demo.isDemoUser(caller);
        ResetStatus r = reset.status();
        SolanaBank b = isDemoUser ? bank.getIfAvailable() : null;
        ReputationLedger rep = isDemoUser ? reputation.getIfAvailable() : null;
        return new DemoStatus(demo.view(), isDemoUser, r.state().name(), r.message(),
                b == null ? null : b.authority().address(),
                b == null ? null : solana.explorerAddressUrl(b.authority().address()),
                b == null ? null : solana.explorerAddressUrl(b.microloan().pool()),
                rep == null ? null : solana.explorerAddressUrl(rep.program().config()));
    }

    private AppUser user(Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new IllegalArgumentException("unknown user"));
    }
}
