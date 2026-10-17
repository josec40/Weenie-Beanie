package com.shbean.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "nessie_customer_id")
    private String nessieCustomerId;

    @Column(name = "nessie_checking_account_id")
    private String nessieCheckingAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash) {
        this.email = email.toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNessieCustomerId() {
        return nessieCustomerId;
    }

    public String getNessieCheckingAccountId() {
        return nessieCheckingAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void linkNessie(String customerId, String checkingAccountId) {
        this.nessieCustomerId = customerId;
        this.nessieCheckingAccountId = checkingAccountId;
    }
}
