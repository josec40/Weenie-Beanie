package com.shbean.nessie;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestClient;

class NessieClientTest {

    private static final String KEY = "secret-key";

    private MockRestServiceServer server;
    private NessieClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NessieClient(builder, new NessieProperties("http://nessie.test", KEY));
    }

    @Test
    void getAccountAppendsKeyAndMapsResponse() {
        server.expect(requestTo("http://nessie.test/accounts/abc?key=" + KEY))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"_id":"abc","type":"Checking","nickname":"Main","rewards":0,"balance":1000,"customer_id":"c1"}
                        """, MediaType.APPLICATION_JSON));

        var account = client.getAccount("abc");

        assertThat(account.id()).isEqualTo("abc");
        assertThat(account.customerId()).isEqualTo("c1");
        assertThat(account.balance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        server.verify();
    }

    @Test
    void merchantCategoryAcceptsStringOrArray() {
        server.expect(requestTo("http://nessie.test/merchants/m1?key=" + KEY))
                .andRespond(withSuccess("{\"_id\":\"m1\",\"name\":\"FreshMart\",\"category\":\"Groceries\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://nessie.test/merchants/m2?key=" + KEY))
                .andRespond(withSuccess("{\"_id\":\"m2\",\"name\":\"Cafe\",\"category\":[\"Dining\",\"Food\"]}", MediaType.APPLICATION_JSON));

        assertThat(client.getMerchant("m1").category()).containsExactly("Groceries");
        assertThat(client.getMerchant("m2").category()).containsExactly("Dining", "Food");
        server.verify();
    }

    @Test
    void httpErrorDoesNotLeakKey() {
        server.expect(requestTo("http://nessie.test/accounts/abc?key=" + KEY)).andRespond(withServerError());

        assertThatThrownBy(() -> client.getAccount("abc"))
                .isInstanceOf(NessieException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageNotContaining(KEY)
                .hasNoCause();
    }

    @Test
    void ioErrorDoesNotLeakKey() {
        server.expect(requestTo("http://nessie.test/accounts/abc?key=" + KEY))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.getAccount("abc"))
                .isInstanceOf(NessieException.class)
                .hasMessageNotContaining(KEY)
                .hasNoCause();
    }
}
