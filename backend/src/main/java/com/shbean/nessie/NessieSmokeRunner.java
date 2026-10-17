package com.shbean.nessie;

import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.Address;
import com.shbean.nessie.NessieModels.CreateAccountRequest;
import com.shbean.nessie.NessieModels.CreateCustomerRequest;
import com.shbean.nessie.NessieModels.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("smoke")
public class NessieSmokeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NessieSmokeRunner.class);

    private final NessieClient nessie;

    public NessieSmokeRunner(NessieClient nessie) {
        this.nessie = nessie;
    }

    @Override
    public void run(ApplicationArguments args) {
        Customer created = nessie.createCustomer(new CreateCustomerRequest("Smoke", "Test",
                new Address("1", "Main St", "Tampa", "FL", "33620")));
        log.info("Nessie smoke: created customer {}", created.id());

        Customer fetched = nessie.getCustomer(created.id());
        log.info("Nessie smoke: fetched customer {} {}", fetched.firstName(), fetched.lastName());

        Account account = nessie.createAccount(created.id(),
                new CreateAccountRequest("Checking", "Smoke Checking", 0, new BigDecimal("1000")));
        log.info("Nessie smoke: created account {}", account.id());

        Account fetchedAccount = nessie.getAccount(account.id());
        log.info("Nessie smoke: account {} balance {}", fetchedAccount.id(), fetchedAccount.balance());
    }
}
