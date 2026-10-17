package com.shbean.cashflow;

import java.util.UUID;

import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.cashflow.NessieSyncService.SyncResult;
import com.shbean.user.AppUserRepository;

@Controller
public class SyncController {

    private final NessieSyncService sync;
    private final AppUserRepository users;

    public SyncController(NessieSyncService sync, AppUserRepository users) {
        this.sync = sync;
        this.users = users;
    }

    @MutationMapping
    public SyncResult syncTransactions(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .map(sync::sync)
                .orElse(SyncResult.NONE);
    }
}
