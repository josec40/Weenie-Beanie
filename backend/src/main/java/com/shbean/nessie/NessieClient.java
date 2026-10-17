package com.shbean.nessie;

import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.CreateAccountRequest;
import com.shbean.nessie.NessieModels.CreateCustomerRequest;
import com.shbean.nessie.NessieModels.CreateResponse;
import com.shbean.nessie.NessieModels.Customer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Supplier;

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

    private URI withKey(UriBuilder builder, String path, Object... uriVariables) {
        return builder.path(path).queryParam("key", apiKey).build(uriVariables);
    }

    // Nessie takes the API key in the query string, and RestClient exception messages include the full URI,
    // so the original exception is deliberately not chained.
    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw new NessieException("Nessie " + operation + " failed with HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new NessieException("Nessie " + operation + " failed: " + e.getClass().getSimpleName());
        }
    }
}
