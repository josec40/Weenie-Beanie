package com.shbean.nessie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public final class NessieModels {

    private NessieModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(
            @JsonProperty("street_number") String streetNumber,
            @JsonProperty("street_name") String streetName,
            String city,
            String state,
            String zip) {
    }

    public record CreateCustomerRequest(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            Address address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(
            @JsonProperty("_id") String id,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            Address address) {
    }

    public record CreateAccountRequest(String type, String nickname, int rewards, BigDecimal balance) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
            @JsonProperty("_id") String id,
            String type,
            String nickname,
            int rewards,
            BigDecimal balance,
            @JsonProperty("customer_id") String customerId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateResponse<T>(int code, String message, T objectCreated) {
    }
}
