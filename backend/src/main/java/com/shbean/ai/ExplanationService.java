package com.shbean.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shbean.forecast.ForecastEngine;
import com.shbean.forecast.ForecastEngine.Line;
import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.forecast.ForecastService.Forecast;

/**
 * Plain-language explanations of forecasts and offers. Only aggregated figures (months, amounts, categories, pattern
 * types) are sent to the model: no names, descriptions, merchant or account identifiers. Results are cached by a hash
 * of those facts; a deterministic template is used whenever the model is unavailable.
 */
@Service
public class ExplanationService {

    public enum Source { AI, FALLBACK }

    public record Explanation(String text, Source source) {
    }

    record Driver(String category, String type, BigDecimal amount) {
    }

    record MonthFacts(String month, BigDecimal net, BigDecimal endingBalance, boolean shortfall, List<Driver> drivers) {
    }

    record ForecastFacts(String currentMonth, BigDecimal startingBalance, BigDecimal everydaySpendingPerMonth,
                         List<MonthFacts> months) {
    }

    public record OfferFacts(String shortfallMonth, BigDecimal projectedDeficit, BigDecimal principal, BigDecimal fee,
                             String repaymentMonth, BigDecimal projectedSurplus) {
    }

    private static final int MAX_CHARS = 700;
    private static final String SYSTEM = """
            You are a friendly assistant for a retail bank. Explain the facts to the customer in at most %d short sentences,
            plain language, second person, no bullet points, no markdown.
            Use only the numbers in the facts; format money like $1,234. Months are YYYY-MM; write them as "February 2027".
            Do not give investment or legal advice, do not promise approval of credit, and do not mention being an AI.
            """;

    private final AiGateway ai;
    private final AiCache cache;
    private final ObjectMapper json;

    public ExplanationService(AiGateway ai, AiCache cache, ObjectMapper json) {
        this.ai = ai;
        this.cache = cache;
        this.json = json;
    }

    public Explanation explainForecast(Forecast forecast) {
        ForecastFacts facts = factsOf(forecast);
        return explain("FORECAST", facts, 3,
                "Explain this 12-month cash-flow forecast. Focus on the months where the balance goes below zero, "
                        + "what drives them, and when it recovers. If there are none, say the balance stays positive.",
                () -> fallbackForecast(facts));
    }

    public Explanation explainOffer(OfferFacts facts) {
        return explain("OFFER", facts, 3,
                "Explain this proactive micro-loan offer: why it is offered, how much, the flat fee, and that repayment "
                        + "is drafted automatically in the repayment month.",
                () -> fallbackOffer(facts));
    }

    private Explanation explain(String kind, Object facts, int sentences, String task, Supplier<String> fallback) {
        String factsJson = toJson(facts);
        String key = sha256(kind + "|" + ai.model() + "|" + factsJson);
        Optional<String> cached = cache.explanation(key);
        if (cached.isPresent()) {
            return new Explanation(cached.get(), Source.AI);
        }
        Optional<String> text = ai.text(SYSTEM.formatted(sentences), task + "\nFacts (JSON): " + factsJson)
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .map(t -> t.length() > MAX_CHARS ? t.substring(0, MAX_CHARS) : t);
        if (text.isPresent()) {
            cache.putExplanation(key, kind, text.get(), ai.model());
            return new Explanation(text.get(), Source.AI);
        }
        return new Explanation(fallback.get(), Source.FALLBACK);
    }

    static ForecastFacts factsOf(Forecast f) {
        List<MonthFacts> months = f.months().stream()
                .map(m -> new MonthFacts(m.month().toString(), m.net(), m.endingBalance(), m.shortfall(), drivers(m)))
                .toList();
        return new ForecastFacts(f.currentMonth().toString(), f.startingBalance(), f.discretionaryMonthly(), months);
    }

    /** Lines that make this month differ from a typical month (non-monthly items), aggregated by category and type. */
    private static List<Driver> drivers(Month m) {
        Map<String, BigDecimal> byKey = m.lines().stream()
                .filter(l -> l.source() != ForecastEngine.Source.MONTHLY && l.source() != ForecastEngine.Source.DISCRETIONARY)
                .collect(Collectors.groupingBy(l -> l.category() + "|" + l.source().name(), LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Line::amount, BigDecimal::add)));
        return byKey.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|");
                    return new Driver(parts[0], parts[1].toLowerCase(Locale.ROOT).replace('_', ' '), e.getValue());
                })
                .sorted(Comparator.comparing((Driver d) -> d.amount().abs()).reversed())
                .limit(3)
                .toList();
    }

    static String fallbackForecast(ForecastFacts f) {
        List<MonthFacts> months = f.months();
        if (months.isEmpty()) {
            return "There is not enough information to project your balance yet.";
        }
        Optional<MonthFacts> firstShort = months.stream().filter(MonthFacts::shortfall).findFirst();
        if (firstShort.isEmpty()) {
            return "Your balance is projected to stay above zero for the next %d months, ending around %s."
                    .formatted(months.size(), money(months.getLast().endingBalance()));
        }
        MonthFacts low = months.stream().min(Comparator.comparing(MonthFacts::endingBalance)).orElseThrow();
        StringBuilder sb = new StringBuilder("Your balance is projected to drop below zero in %s, reaching about %s in %s."
                .formatted(label(firstShort.get().month()), money(low.endingBalance()), label(low.month())));
        firstShort.get().drivers().stream().filter(d -> d.amount().signum() < 0).findFirst().ifPresent(d ->
                sb.append(" The main reason is a %s %s payment of %s.".formatted(d.type(), d.category(), money(d.amount().abs()))));
        int idx = months.indexOf(low);
        months.subList(idx, months.size()).stream().filter(m -> m.endingBalance().signum() >= 0).findFirst().ifPresentOrElse(
                r -> sb.append(" It recovers in %s.".formatted(label(r.month()))),
                () -> sb.append(" It does not recover within this forecast."));
        return sb.toString();
    }

    static String fallbackOffer(OfferFacts o) {
        return ("Your forecast shows a shortfall of about %s in %s. We can lend you %s that month for a flat fee of %s. "
                + "It is repaid automatically in %s, when your forecast shows a surplus of about %s.")
                .formatted(money(o.projectedDeficit()), label(o.shortfallMonth()), money(o.principal()), money(o.fee()),
                        label(o.repaymentMonth()), money(o.projectedSurplus()));
    }

    private String toJson(Object facts) {
        try {
            return json.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize explanation facts", e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String money(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
        f.setMaximumFractionDigits(0);
        return f.format(v.setScale(0, RoundingMode.HALF_UP));
    }

    private static String label(String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.US) + " " + ym.getYear();
    }
}
