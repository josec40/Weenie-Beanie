package com.shbean.ai;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.shbean.ai.ExpenseParser.ExpenseDraft;
import com.shbean.ai.ExplanationService.Explanation;
import com.shbean.demo.DemoClock;
import com.shbean.forecast.ForecastService.Forecast;

@Controller
public class AiController {

    private final ExpenseParser parser;
    private final ExplanationService explanations;
    private final DemoClock clock;

    public AiController(ExpenseParser parser, ExplanationService explanations, DemoClock clock) {
        this.parser = parser;
        this.explanations = explanations;
        this.clock = clock;
    }

    @QueryMapping
    public List<String> categories() {
        return Categories.ALL;
    }

    @MutationMapping
    public List<ExpenseDraft> draftRecurringExpenses(@Argument String text) {
        return parser.parse(text, clock.currentMonth());
    }

    @SchemaMapping(typeName = "Forecast")
    public Explanation explanation(Forecast forecast) {
        return explanations.explainForecast(forecast);
    }
}
