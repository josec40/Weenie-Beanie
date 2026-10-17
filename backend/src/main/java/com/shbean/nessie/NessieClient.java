package com.shbean.nessie;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.Bill;
import com.shbean.nessie.NessieModels.BillRequest;
import com.shbean.nessie.NessieModels.CreateAccountRequest;
import com.shbean.nessie.NessieModels.CreateCustomerRequest;
import com.shbean.nessie.NessieModels.CreateMerchantRequest;
import com.shbean.nessie.NessieModels.CreatePurchaseRequest;
import com.shbean.nessie.NessieModels.CreateResponse;
import com.shbean.nessie.NessieModels.CreateTransferRequest;
import com.shbean.nessie.NessieModels.Customer;
import com.shbean.nessie.NessieModels.Merchant;
import com.shbean.nessie.NessieModels.Purchase;
import com.shbean.nessie.NessieModels.Transfer;

@Component
public class NessieClient {

    private final RestClient rest;
    private final String apiKey;

    public NessieClient(RestClient.Builder builder, NessieProperties props) {
        this.rest = builder.baseUrl(props.baseUrl()).build();
        this.apiKey = props.apiKey();
    }

    public Customer createCustomer(CreateCustomerRequest request) {
        return call("createCustomer", () -> rest.post()
                .uri(b -> withKey(b, "/customers"))
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<CreateResponse<Customer>>() {})
                .objectCreated());
    }

    public Customer getCustomer(String customerId) {
        return call("getCustomer", () -> rest.get()
                .uri(b -> withKey(b, "/customers/{id}", customerId))
                .retrieve()
                .body(Customer.class));
    }

    public Account createAccount(String customerId, CreateAccountRequest request) {
        return call("createAccount", () -> rest.post()
                .uri(b -> withKey(b, "/customers/{id}/accounts", customerId))
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<CreateResponse<Account>>() {})
                .objectCreated());
    }

    public Account getAccount(String accountId) {
        return call("getAccount", () -> rest.get()
                .uri(b -> withKey(b, "/accounts/{id}", accountId))
                .retrieve()
                .body(Account.class));
    }

    public List<Transfer> listDeposits(String accountId) {
        return list("listDeposits", "/accounts/{id}/deposits", accountId, new ParameterizedTypeReference<>() {});
    }

    public List<Transfer> listWithdrawals(String accountId) {
        return list("listWithdrawals", "/accounts/{id}/withdrawals", accountId, new ParameterizedTypeReference<>() {});
    }

    public List<Purchase> listPurchases(String accountId) {
        return list("listPurchases", "/accounts/{id}/purchases", accountId, new ParameterizedTypeReference<>() {});
    }

    public Transfer createDeposit(String accountId, CreateTransferRequest request) {
        return create("createDeposit", "/accounts/{id}/deposits", accountId, request, new ParameterizedTypeReference<>() {});
    }

    public Transfer createWithdrawal(String accountId, CreateTransferRequest request) {
        return create("createWithdrawal", "/accounts/{id}/withdrawals", accountId, request, new ParameterizedTypeReference<>() {});
    }

    public Purchase createPurchase(String accountId, CreatePurchaseRequest request) {
        return create("createPurchase", "/accounts/{id}/purchases", accountId, request, new ParameterizedTypeReference<>() {});
    }

    public Merchant getMerchant(String merchantId) {
        return call("getMerchant", () -> rest.get()
                .uri(b -> withKey(b, "/merchants/{id}", merchantId))
                .retrieve()
                .body(Merchant.class));
    }

    public Merchant createMerchant(CreateMerchantRequest request) {
        return call("createMerchant", () -> rest.post()
                .uri(b -> withKey(b, "/merchants"))
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<CreateResponse<Merchant>>() {})
                .objectCreated());
    }

    public Bill createBill(String accountId, BillRequest request) {
        return create("createBill", "/accounts/{id}/bills", accountId, request, new ParameterizedTypeReference<>() {});
    }

    public void updateBill(String billId, BillRequest request) {
        call("updateBill", () -> rest.put()
                .uri(b -> withKey(b, "/bills/{id}", billId))
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteBill(String billId) {
        call("deleteBill", () -> rest.delete()
                .uri(b -> withKey(b, "/bills/{id}", billId))
                .retrieve()
                .toBodilessEntity());
    }

    private <T> List<T> list(String operation, String path, String id, ParameterizedTypeReference<List<T>> type) {
        List<T> result = call(operation, () -> rest.get()
                .uri(b -> withKey(b, path, id))
                .retrieve()
                .body(type));
        return result == null ? List.of() : result;
    }

    private <T> T create(String operation, String path, String id, Object request,
                         ParameterizedTypeReference<CreateResponse<T>> type) {
        return call(operation, () -> rest.post()
                .uri(b -> withKey(b, path, id))
                .body(request)
                .retrieve()
                .body(type)
                .objectCreated());
    }

    private URI withKey(UriBuilder builder, String path, Object... uriVariables) {
        return builder.path(path).queryParam("key", apiKey).build(uriVariables);
    }

    // Nessie takes the API key in the query string, and RestClient exception messages include the full URI,
    // so the original exception is deliberately not chained.
    private <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw new NessieException("Nessie " + operation + " failed with HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            Throwable root = e.getMostSpecificCause();
            String detail = root == e ? "" : ": " + root.getClass().getSimpleName() + " " + redact(root.getMessage());
            throw new NessieException("Nessie " + operation + " failed: " + e.getClass().getSimpleName() + detail);
        }
    }

    private String redact(String message) {
        return message == null || apiKey == null || apiKey.isEmpty() ? message : message.replace(apiKey, "****");
    }
}
