package com.shbean.cashflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "nessie.sync.enabled", havingValue = "true", matchIfMissing = true)
public class NessieSyncScheduler {

    private final NessieSyncService sync;

    public NessieSyncScheduler(NessieSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(fixedDelayString = "${nessie.sync.interval:PT15M}", initialDelayString = "${nessie.sync.initial-delay:PT1M}")
    public void poll() {
        sync.syncAll();
    }
}
