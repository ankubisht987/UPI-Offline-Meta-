package com.ankush.upi_offline_meta.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account {
    
    @Id
    private String vpa; // Virtual Payment Address, e.g., "user@bank"

    @Column(nullable = false)
    private String holderName; // Name of the account holder

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance; // Current balance in the account
    
    @Version
    private Long version; // For optimistic locking

    @Column(nullable = false, updatable = false)
    private Instant createdAt; // Timestamp when the account was created

    @PrePersist
    protected void onCreate(){
        createdAt = Instant.now();
    }

    // ============================================
    // CONSTRUCTORS
    // ============================================
    public Account() {
    }
    public Account(String vpa, String holderName, BigDecimal balance) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.balance = balance;
    }

    // ============================================
    // GETTERS & SETTERS
    // ============================================
    public String getVpa() {
        return vpa;
    }

    public void setVpa(String vpa) {
        this.vpa = vpa;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Account{" +
                "vpa='" + vpa + '\'' +
                ", holderName='" + holderName + '\'' +
                ", balance=" + balance +
                ", version=" + version +
                '}';
    }
    
}
