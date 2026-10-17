package com.shbean.forecast;

import java.util.UUID;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.forecast.ForecastService.Forecast;
import com.shbean.user.AppUserRepository;

@Controller
public class ForecastController {

    private final ForecastService forecasts;
    private final AppUserRepository users;

    public ForecastController(ForecastService forecasts, AppUserRepository users) {
        this.forecasts = forecasts;
        this.users = users;
    }

    @QueryMapping
    public Forecast forecast(@Argument Integer months, @AuthenticationPrincipal Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .map(u -> forecasts.forecast(u, months == null ? 12 : months))
                .orElse(null);
    }
}
