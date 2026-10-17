package com.shbean.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.demo")
public record DemoProperties(String email, String password, @DefaultValue("true") boolean seedNessie) {
}
