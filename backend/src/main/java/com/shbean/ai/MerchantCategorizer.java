package com.shbean.ai;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

/** Maps a merchant to a canonical category: Nessie category, then cache, then keyword rules, then Gemini. */
@Service
public class MerchantCategorizer {

    public static final String UNCATEGORIZED = "Uncategorized";

    record CategoryAnswer(String category) {
    }

    private static final String SYSTEM = """
            You classify a merchant into exactly one spending category.
            Allowed categories: %s.
            Answer with the single best category from the list. If unsure, answer "Other".
            """.formatted(String.join(", ", Categories.ALL));

    private final AiCache cache;
    private final AiGateway ai;

    public MerchantCategorizer(AiCache cache, AiGateway ai) {
        this.cache = cache;
        this.ai = ai;
    }

    public String categorize(String merchantName, String nessieCategory) {
        Optional<String> canonical = Categories.canonical(nessieCategory);
        if (canonical.isPresent()) {
            return canonical.get();
        }
        if (merchantName == null || merchantName.isBlank()) {
            return UNCATEGORIZED;
        }
        String key = normalize(merchantName);
        Optional<String> cached = cache.merchantCategory(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        Optional<String> rule = Categories.byKeyword(merchantName);
        if (rule.isPresent()) {
            cache.putMerchantCategory(key, rule.get(), "RULES");
            return rule.get();
        }
        // Merchant names are business names, not customer data; still redacted defensively.
        Optional<String> answer = ai.entity(SYSTEM, "Merchant: " + Categories.redact(merchantName.strip()), CategoryAnswer.class)
                .map(CategoryAnswer::category)
                .flatMap(Categories::canonical);
        answer.ifPresent(c -> cache.putMerchantCategory(key, c, "AI"));
        return answer.orElse(UNCATEGORIZED);
    }

    static String normalize(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9&' ]", " ").replaceAll("\\s+", " ").strip();
        return n.length() > 120 ? n.substring(0, 120) : n;
    }
}
