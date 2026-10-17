package com.shbean.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.notification.NotificationService.Notification;

@Controller
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @QueryMapping
    public List<Notification> notifications(@Argument Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return notifications.list(userId(jwt), limit == null ? 20 : limit);
    }

    @QueryMapping
    public int unreadNotifications(@AuthenticationPrincipal Jwt jwt) {
        return notifications.unread(userId(jwt));
    }

    @MutationMapping
    public int markNotificationsRead(@Argument List<UUID> ids, @AuthenticationPrincipal Jwt jwt) {
        return notifications.markRead(userId(jwt), ids);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
