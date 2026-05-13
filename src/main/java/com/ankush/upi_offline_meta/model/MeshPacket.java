package com.ankush.upi_offline_meta.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


/**
 * MeshPacket is the data packet that moves from one phone to another
 * in the offline Bluetooth payment network.
 *
 * Example flow:
 * Your Phone -> Nearby Phone -> Another Phone -> Server
 *
 * Every nearby phone helps forward the packet until it finally reaches
 * the backend server.
 *
 * The packet contains 2 types of information:
 *
 * 1. Outer Information (visible to relay phones)
 *    These fields help phones forward the packet correctly:
 *
 *    - packetId  -> unique ID of the packet
 *    - ttl       -> how many more phones can forward it
 *    - createdAt -> when the packet was created
 *
 * 2. Secret Information (hidden from relay phones)
 *    The actual payment details are inside `ciphertext`.
 *    This part is encrypted, so nearby phones cannot read it.
 *    Only the backend server can decrypt and understand it.
 *
 * Why ttl is important:
 * If phones keep forwarding forever, the network becomes flooded.
 * So each phone reduces ttl by 1.
 *
 * Example:
 * ttl = 5
 * Next phone -> 4
 * Next phone -> 3
 * ...
 * When ttl becomes 0, forwarding stops.
 *
 * Security:
 * Even if another phone sees the packet, it cannot see:
 * - bank details
 * - amount
 * - UPI ID
 * - payment information
 *
 * because everything important is encrypted inside ciphertext.
 */


public class MeshPacket  {
    
    @NotBlank
    private String packetId; // Unique packet ID used to avoid duplicates

    @Min(0)
    private int ttl; // Remaining number of times packet can be forwarded

    @NotNull
    private Long createdAt; // Time when packet was created (epoch millis)

    @NotBlank
    private String ciphertext; // Encrypted payment data
    
    public MeshPacket() {
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    
    
}
