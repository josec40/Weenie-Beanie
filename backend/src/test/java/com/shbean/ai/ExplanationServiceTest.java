package com.shbean.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shbean.ai.ExplanationService.Explanation;
import com.shbean.ai.ExplanationService.OfferFacts;
import com.shbean.ai.ExplanationService.Source;
import com.shbean.demo.DemoScenario;
import com.shbean.forecast.ForecastEngine;
import com.shbean.forecast.ForecastEngine.HistoryTxn;
import com.shbean.forecast.ForecastService.Forecast;

class ExplanationServiceTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);

    private final AiGateway ai = mock(AiGateway.class);
    private final AiCache cache = mock(AiCache.class);
    private final ExplanationService service = new ExplanationService(ai, cache, new ObjectMapper());

    @Test
    void fallbackNamesShortfallDriverAndRecovery() {
        when(ai.text(anyString(), anyString())).thenReturn(Optional.empty());
        when(cache.explanation(anyString())).thenReturn(Optional.empty());

        Explanation e = service.explainForecast(demoForecast());

        assertThat(e.source()).isEqualTo(Source.FALLBACK);
        assertThat(e.text())
                .contains("February 2027")
                .contains("annual Insurance payment of $1,250")
                .contains("It recovers in April 2027");
    }

    @Test
    void aiTextIsCachedAndOnlyAggregatesAreSent() {
        when(ai.model()).thenReturn("gemini-test");
        when(cache.explanation(anyString())).thenReturn(Optional.empty());
        when(ai.text(anyString(), anyString())).thenReturn(Optional.of("  Your balance dips in February 2027.  "));

        Explanation e = service.explainForecast(demoForecast());

        assertThat(e).isEqualTo(new Explanation("Your balance dips in February 2027.", Source.AI));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).text(anyString(), prompt.capture());
        assertThat(prompt.getValue())
                .contains("\"category\":\"Insurance\"")
                .doesNotContain("Annual auto insurance premium")
                .doesNotContain("Payroll")
                .doesNotContain("Acme");
        verify(cache).putExplanation(anyString(), eq("FORECAST"), eq("Your balance dips in February 2027."), eq("gemini-test"));
    }

    @Test
    void cachedExplanationSkipsTheModel() {
        when(cache.explanation(anyString())).thenReturn(Optional.of("cached text"));

        assertThat(service.explainForecast(demoForecast())).isEqualTo(new Explanation("cached text", Source.AI));
        verify(ai, never()).text(anyString(), anyString());
    }

    @Test
    void offerFallbackStatesAmountsAndMonths() {
        when(ai.text(anyString(), anyString())).thenReturn(Optional.empty());
        when(cache.explanation(anyString())).thenReturn(Optional.empty());

        Explanation e = service.explainOffer(new OfferFacts("2027-02", new BigDecimal("342"), new BigDecimal("350"),
                new BigDecimal("10.50"), "2027-04", new BigDecimal("1352")));

        assertThat(e.text()).isEqualTo("Your forecast shows a shortfall of about $342 in February 2027. We can lend you "
                + "$350 that month for a flat fee of $11. It is repaid automatically in April 2027, when your forecast "
                + "shows a surplus of about $1,352.");
    }

    private static Forecast demoForecast() {
        List<HistoryTxn> history = DemoScenario.history(NOW, 42L).stream()
                .map(t -> new HistoryTxn(t.date(),
                        t.kind() == DemoScenario.Kind.DEPOSIT ? t.amount() : t.amount().negate(),
                        t.merchantKey() == null ? (t.kind() == DemoScenario.Kind.DEPOSIT ? "Income" : "Cash")
                                : DemoScenario.MERCHANTS.stream().filter(m -> m.key().equals(t.merchantKey())).findFirst().orElseThrow().category(),
                        t.description(), t.kind() + ":" + (t.merchantKey() != null ? t.merchantKey() : t.description())))
                .toList();
        Map<YearMonth, BigDecimal> net = history.stream().collect(Collectors.groupingBy(t -> YearMonth.from(t.date()),
                Collectors.reducing(BigDecimal.ZERO, HistoryTxn::amount, BigDecimal::add)));
        var r = ForecastEngine.forecast(new ForecastEngine.Input(NOW, 12, DemoScenario.CURRENT_BALANCE, history, net, List.of()));
        return new Forecast(r.currentMonth(), r.startingBalance(), null, r.discretionaryMonthly(), r.months());
    }
}
