package com.shbean.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shbean.TestInfra;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long!!",
        "nessie.api-key=test",
        "nessie.base-url=http://localhost:1",
        "spring.ai.model.chat=none"
})
@Import(TestInfra.class)
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String EMAIL = "alex@demo.local";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String ME_QUERY = "{\"query\":\"{ me { email nessieCustomerId } }\"}";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        if (!users.existsByEmail(EMAIL)) {
            users.save(new AppUser(EMAIL, passwordEncoder.encode(PASSWORD)));
        }
    }

    @Test
    void loginThenQueryMe() throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode token = objectMapper.readTree(body);

        MvcResult pending = mvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ME_QUERY))
                .andReturn();

        // Spring GraphQL completes synchronously when the result is already available.
        MvcResult result = pending.getRequest().isAsyncStarted()
                ? mvc.perform(asyncDispatch(pending)).andReturn()
                : pending;

        status().isOk().match(result);
        jsonPath("$.data.me.email").value(EMAIL).match(result);
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void graphqlRequiresToken() throws Exception {
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(ME_QUERY))
                .andExpect(status().isUnauthorized());
    }
}
