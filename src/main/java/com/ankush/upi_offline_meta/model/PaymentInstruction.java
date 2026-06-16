package com.ankush.upi_offline_meta.model;

import java.math.BigDecimal;

public class PaymentInstruction {
    
    private String senderVpa; // VPA of the sender (e.g., ankush@bank)
    private String receiverVpa; // VPA of the receiver (e.g., bob@bank)
    private BigDecimal amount; // Amount to be transferred, stored as String to avoid precision issues
    private String pinHash; // Hash of the sender's PIN for authentication (never store plain PIN)
    private String nonce; // Unique random string to prevent replay attacks (e.g., "abc123xyz")
    private Long signedAt; // Timestamp (in milliseconds) when the instruction was signed by the sender (e.g., 1690000000000)

    // ============================================
    // CONSTRUCTORS
    public PaymentInstruction() {
    }

    public PaymentInstruction(BigDecimal amount, String nonce, String pinHash, String receiverVpa, String senderVpa, Long signedAt) {
        this.amount = amount;
        this.nonce = nonce;
        this.pinHash = pinHash;
        this.receiverVpa = receiverVpa;
        this.senderVpa = senderVpa;
        this.signedAt = signedAt;
    }

    // ============================================
    // GETTERS & SETTERS

    public String getSenderVpa() {
        return senderVpa;
    }

    public void setSenderVpa(String senderVpa) {
        this.senderVpa = senderVpa;
    }

    public String getReceiverVpa() {
        return receiverVpa;
    }

    public void setReceiverVpa(String receiverVpa) {
        this.receiverVpa = receiverVpa;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Long getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Long signedAt) {
        this.signedAt = signedAt;
    }

}
