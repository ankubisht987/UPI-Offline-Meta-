package com.ankush.upi_offline_meta.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;


/**
 * Account represents a user's bank/wallet account inside the system.
 *
 * Each account is identified using a VPA (Virtual Payment Address),
 * similar to real UPI IDs like:
 *
 *    ankush@bank
 *    user@upi
 *
 * This entity is stored in the database using JPA/Hibernate.
 *
 * Main responsibilities:
 * - Store account holder information
 * - Store current account balance
 * - Support safe balance updates during transactions
 * - Keep track of account creation time
 *
 * Important Fields:
 *
 * - vpa:
 *     Unique UPI-style address used as the account ID.
 *
 * - holderName:
 *     Name of the account owner.
 *
 * - balance:
 *     Current money available in the account.
 *     BigDecimal is used instead of double to avoid money calculation errors.
 *
 * - version:
 *     Used for optimistic locking.
 *     Helps prevent two transactions from updating the same account balance
 *     at the same time and causing incorrect results.
 *
 * - createdAt:
 *     Timestamp showing when the account was created.
 *
 * Lifecycle:
 * - @PrePersist automatically sets createdAt before saving
 *   the account for the first time.
 *
 * Database Notes:
 * - This class is mapped to the "accounts" table.
 * - JPA/Hibernate automatically converts this object into a database row.
 */


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
