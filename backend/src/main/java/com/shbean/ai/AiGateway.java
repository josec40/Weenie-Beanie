package com.shbean.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single entry point to Gemini. Every call is time-boxed and failure-tolerant: callers get {@link Optional#empty()}
 * and use their deterministic fallback. After a failure, AI is skipped for a cooldown period.
 * Prompts are never logged.
 */
@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final ChatClient client;
    private final String model;
    private final Duration timeout;
    private final Duration cooldown;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Instant pausedUntil = Instant.EPOCH;

    public AiGateway(ObjectProvider<ChatModel> chatModel,
                     @Value("${spring.ai.google.genai.chat.options.model:unknown}") String model,
                     @Value("${app.ai.timeout:PT15S}") Duration timeout,
                     @Value("${app.ai.cooldown:PT60S}") Duration cooldown) {
        ChatModel m = chatModel.getIfAvailable();
        this.client = m == null ? null : ChatClient.create(m);
        this.model = model;
        this.timeout = timeout;
        this.cooldown = cooldown;
        log.info("AI gateway {}", client == null ? "disabled (no chat model configured)" : "enabled with model " + model);
    }

    public boolean available() {
        return client != null && Instant.now().isAfter(pausedUntil);
    }

    public String model() {
        return model;
    }

    public Optional<String> text(String system, String user) {
        return run(c -> c.prompt().system(system).user(user).call().content());
    }

    public <T> Optional<T> entity(String system, String user, Class<T> type) {
        return run(c -> c.prompt().system(system).user(user).call().entity(type));
    }

    private <T> Optional<T> run(Function<ChatClient, T> call) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(CompletableFuture.supplyAsync(() -> call.apply(client), executor)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("AI call failed ({}); using fallback for {}", cause.getClass().getSimpleName(), cooldown);
            pausedUntil = Instant.now().plus(cooldown);
            return Optional.empty();
        }
    }
}
