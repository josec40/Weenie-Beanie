package com.shbean.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.demo")
public record DemoProperties(String email, String password) {
}
