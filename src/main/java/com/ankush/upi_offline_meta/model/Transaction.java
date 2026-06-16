package com.ankush.upi_offline_meta.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions", indexes={
    @Index(name = "idx_packet_hash", columnList = "packetHash", unique = true)
})
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Auto-generated unique ID for each transaction
    
    @Column(nullable = false, unique = true, length = 64)
    private String packetHash; // Unique hash of the payment instruction packet (e.g., SHA-256 hash)

    @Column(nullable = false)
    private String senderVpa; // VPA of the sender (e.g., ankush@bank)

    @Column(nullable = false)
    private String receiverVpa; // VPA of the receiver (e.g., bob@

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount; // Amount transferred in this transaction

    @Column(nullable = false)
    private Instant signedAt; // Timestamp when the transaction was signed by the sender

    @Column(nullable = false)
    private Instant settledAt; // Timestamp when the transaction was settled by the backend server

    @Column(nullable = false)
    private String bridgeNodeId; // ID of the relay phone that forwarded the packet to the server (e.g., "phone123")

    @Column(nullable = false)
    private int hopCount; // Number of relay phones the packet passed through before reaching the server (e.g., 3)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status; // Status of the transaction (e.g., PENDING, SUCCESS, FAILED)

    public enum Status{SETTLED, REJECTED}

    // ============================================
    // CONSTRUCTORS
    public Transaction() {
    }
    
    // public Transaction(String packetHash, String senderVpa, String receiverVpa, BigDecimal amount, Instant signedAt, Instant settledAt, String bridgeNodeId, int hopCount, Status status) {
    //     this.packetHash = packetHash;
    //     this.senderVpa = senderVpa;
    //     this.receiverVpa = receiverVpa;
    //     this.amount = amount;
    //     this.signedAt = signedAt;
    //     this.settledAt = settledAt;
    //     this.bridgeNodeId = bridgeNodeId;
    //     this.hopCount = hopCount;
    //     this.status = status;
    // }

    // ============================================
    // GETTERS & SETTERS

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPacketHash() {
        return packetHash;
    }

    public void setPacketHash(String packetHash) {
        this.packetHash = packetHash;
    }

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

    public Instant getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public String getBridgeNodeId() {
        return bridgeNodeId;
    }

    public void setBridgeNodeId(String bridgeNodeId) {
        this.bridgeNodeId = bridgeNodeId;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
    

}
