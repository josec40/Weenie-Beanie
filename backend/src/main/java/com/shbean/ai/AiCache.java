package com.shbean.ai;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistent caches for AI output so the same facts never hit the model twice. */
@Repository
public class AiCache {

    private final JdbcTemplate jdbc;

    public AiCache(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> explanation(String cacheKey) {
        return jdbc.queryForList("SELECT text FROM ai_explanation WHERE cache_key = ?", String.class, cacheKey)
                .stream().findFirst();
    }

    public void putExplanation(String cacheKey, String kind, String text, String model) {
        jdbc.update("""
                INSERT INTO ai_explanation (cache_key, kind, text, model) VALUES (?, ?, ?, ?)
                ON CONFLICT (cache_key) DO NOTHING
                """, cacheKey, kind, text, model);
    }

    public Optional<String> merchantCategory(String nameKey) {
        return jdbc.queryForList("SELECT category FROM merchant_category WHERE name_key = ?", String.class, nameKey)
                .stream().findFirst();
    }

    public void putMerchantCategory(String nameKey, String category, String source) {
        jdbc.update("""
                INSERT INTO merchant_category (name_key, category, source) VALUES (?, ?, ?)
                ON CONFLICT (name_key) DO NOTHING
                """, nameKey, category, source);
    }
}
