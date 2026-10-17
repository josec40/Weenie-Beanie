package com.shbean.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Canonical spending categories, keyword rules, and redaction applied to any free text sent to the model. */
public final class Categories {

    public static final List<String> ALL = List.of(
            "Housing", "Utilities", "Groceries", "Dining", "Transportation", "Insurance", "Loans", "Entertainment",
            "Subscriptions", "Health", "Shopping", "Education", "Childcare", "Personal Care", "Travel", "Gifts & Donations",
            "Other");

    private static final Map<Pattern, String> KEYWORDS = new LinkedHashMap<>();

    static {
        put("Housing", "rent", "mortgage", "apartment", "landlord", "hoa", "property tax");
        put("Utilities", "electric", "power", "water", "gas bill", "internet", "wifi", "wireless", "phone", "mobile",
                "comcast", "verizon", "at&t", "t-mobile", "utility");
        put("Insurance", "insurance", "geico", "progressive", "allstate", "state farm");
        put("Loans", "loan", "student", "auto finance", "credit card", "sallie", "navient");
        put("Entertainment", "netflix", "hulu", "disney", "hbo", "spotify", "youtube", "cinema", "movie", "xbox",
                "playstation", "steam");
        put("Subscriptions", "subscription", "membership", "prime", "icloud", "patreon");
        put("Health", "gym", "fitness", "pharmacy", "cvs", "walgreens", "doctor", "dental", "clinic");
        put("Groceries", "grocery", "groceries", "supermarket", "whole foods", "trader joe", "kroger", "publix", "aldi",
                "freshmart");
        put("Dining", "cafe", "coffee", "restaurant", "starbucks", "pizza", "burger", "doordash", "uber eats", "grubhub",
                "lunch", "dinner");
        put("Transportation", "fuel", "gas station", "shell", "chevron", "exxon", "uber", "lyft", "parking", "transit",
                "metro card", "toll", "car payment");
        put("Education", "tuition", "school", "course", "textbook");
        put("Childcare", "daycare", "childcare", "babysit", "nanny");
        put("Travel", "airline", "hotel", "airbnb", "flight");
        put("Shopping", "amazon", "target", "walmart", "mart", "store", "clothing", "shop");
    }

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d[\\d\\s-]{6,}\\d");

    private Categories() {
    }

    /** Case-insensitive match against the canonical list. */
    public static Optional<String> canonical(String category) {
        if (category == null) {
            return Optional.empty();
        }
        return ALL.stream().filter(c -> c.equalsIgnoreCase(category.strip())).findFirst();
    }

    public static Optional<String> byKeyword(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.toLowerCase(Locale.ROOT);
        return KEYWORDS.entrySet().stream()
                .filter(e -> e.getKey().matcher(t).find())
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** Strips e-mail addresses and long digit runs (account/card/phone numbers) before text leaves the bank. */
    public static String redact(String text) {
        String t = EMAIL.matcher(text).replaceAll("[email]");
        return LONG_NUMBER.matcher(t).replaceAll("[number]");
    }

    private static void put(String category, String... keywords) {
        for (String k : keywords) {
            KEYWORDS.putIfAbsent(Pattern.compile("(?<![a-z])" + Pattern.quote(k)), category);
        }
    }
}
