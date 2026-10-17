package com.shbean.nessie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    /** Deposit or withdrawal. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transfer(
            @JsonProperty("_id") String id,
            @JsonProperty("transaction_date") LocalDate transactionDate,
            String status,
            String medium,
            BigDecimal amount,
            String description) {
    }

    public record CreateTransferRequest(
            String medium,
            @JsonProperty("transaction_date") LocalDate transactionDate,
            String status,
            BigDecimal amount,
            String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Purchase(
            @JsonProperty("_id") String id,
            @JsonProperty("merchant_id") String merchantId,
            @JsonProperty("purchase_date") LocalDate purchaseDate,
            String status,
            String medium,
            BigDecimal amount,
            String description) {
    }

    public record CreatePurchaseRequest(
            @JsonProperty("merchant_id") String merchantId,
            String medium,
            @JsonProperty("purchase_date") LocalDate purchaseDate,
            BigDecimal amount,
            String status,
            String description) {
    }

    public record Geocode(double lat, double lng) {
    }

    // The live API returns category as a string, while older Nessie docs show an array.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Merchant(
            @JsonProperty("_id") String id,
            String name,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> category) {
    }

    public record CreateMerchantRequest(String name, String category, Address address, Geocode geocode) {
    }

    public record BillRequest(
            String status,
            String payee,
            String nickname,
            @JsonProperty("payment_date") LocalDate paymentDate,
            @JsonProperty("recurring_date") int recurringDate,
            @JsonProperty("payment_amount") BigDecimal paymentAmount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bill(@JsonProperty("_id") String id, String payee, String status) {
    }
}
