package com.shbean.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code seedCredentialHistory}: dev-only, records 2 prior on-time repayments so the demo user starts at Tier 1. */
@ConfigurationProperties("app.demo")
public record DemoProperties(String email, String password, @DefaultValue("true") boolean seedNessie,
                             @DefaultValue("false") boolean seedCredentialHistory) {
}
