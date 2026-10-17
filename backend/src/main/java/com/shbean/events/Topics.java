package com.shbean.events;

import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** All event topics. Broker auto-create is disabled, so every topic is declared here. */
@Configuration
public class Topics {

    public static final String TRANSACTIONS_INGESTED = "transactions.ingested";
    public static final String CASHFLOW_CHANGED = "cashflow.changed";
    public static final String FORECAST_UPDATED = "forecast.updated";
    public static final String OFFER_GENERATED = "offer.generated";
    public static final String LOAN_ACCEPTED = "loan.accepted";
    public static final String LOAN_DISBURSEMENT_REQUESTED = "loan.disbursement.requested";
    public static final String LOAN_DISBURSED = "loan.disbursed";
    public static final String LOAN_REPAYMENT_REQUESTED = "loan.repayment.requested";
    public static final String LOAN_REPAID = "loan.repaid";
    public static final String LOAN_DEFAULTED = "loan.defaulted";
    public static final String LOAN_FIAT_SETTLED = "loan.fiat.settled";
    public static final String LOAN_FIAT_FAILED = "loan.fiat.failed";
    public static final String CREDENTIAL_UPDATE_REQUESTED = "credential.update.requested";
    public static final String CREDENTIAL_UPDATED = "credential.updated";

    public static final List<String> ALL = List.of(
            TRANSACTIONS_INGESTED, CASHFLOW_CHANGED, FORECAST_UPDATED, OFFER_GENERATED, LOAN_ACCEPTED,
            LOAN_DISBURSEMENT_REQUESTED, LOAN_DISBURSED, LOAN_REPAYMENT_REQUESTED, LOAN_REPAID, LOAN_DEFAULTED,
            LOAN_FIAT_SETTLED, LOAN_FIAT_FAILED, CREDENTIAL_UPDATE_REQUESTED, CREDENTIAL_UPDATED);

    static final int PARTITIONS = 3;

    @Bean
    public KafkaAdmin.NewTopics eventTopics() {
        return new KafkaAdmin.NewTopics(ALL.stream()
                .map(t -> TopicBuilder.name(t).partitions(PARTITIONS).replicas(1).build())
                .toArray(NewTopic[]::new));
    }
}
