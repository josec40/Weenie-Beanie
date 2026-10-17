package com.shbean.user;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
public class MeController {

    public record UserView(UUID id, String email, String nessieCustomerId, String nessieCheckingAccountId) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getEmail(), u.getNessieCustomerId(), u.getNessieCheckingAccountId());
        }
    }

    private final AppUserRepository users;

    public MeController(AppUserRepository users) {
        this.users = users;
    }

    @QueryMapping
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject())).map(UserView::of).orElse(null);
    }
}
