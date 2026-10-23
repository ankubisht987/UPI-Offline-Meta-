package com.ankush.upi_offline_meta.services;

public record IngestResult(String outcome, // SETTLED / REJECTED / DUPLICATE_DROPPED / INVALID
        String packetHash,
        Long transactionId, // null unless it was recorded
        String transactionStatus) {
}