package com.ankush.upi_offline_meta.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ankush.upi_offline_meta.crypto.HybridCryptoService;
import com.ankush.upi_offline_meta.model.MeshPacket;
import com.ankush.upi_offline_meta.model.PaymentInstruction;
import com.ankush.upi_offline_meta.model.Transaction;

@Service
public class BridgeIngestionService {
    /** A payment older than this is refused. */
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000; // 24 hours
    /** Allow a little clock drift into the future. */
    private static final long MAX_FUTURE_SKEW_MS = 5L * 60 * 1000; // 5 minutes

    @Autowired
    private HybridCryptoService crypto;
    @Autowired
    private IdempotencyService idem;
    @Autowired
    private SettlementService settlement;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {

        // ---- 1. fingerprint ----
        // String hash = crypto.hashCiphertext(packet.getCiphertext());
        String hash;
        try {
            hash = crypto.hashCiphertext(packet.getCiphertext());
        } catch (Exception e) {
            return new IngestResult("INVALID", null, null, "HASH_FAILED");
        }

        // ---- 2. claim (duplicate guard) ----
        if (!idem.claim(hash)) {
            return new IngestResult("DUPLICATE_DROPPED", hash, null, null);
        }

        // ---- 3. decrypt (tamper check) ----
        PaymentInstruction instruction;
        try {
            instruction = crypto.decrypt(packet.getCiphertext());
        } catch (Exception e) {
            return new IngestResult("INVALID", hash, null, "TAMPERED_OR_CORRUPT");
        }

        // ---- 4. freshness ----
        long now = System.currentTimeMillis();
        Long signedAt = instruction.getSignedAt();
        if (signedAt == null
                || signedAt < now - MAX_AGE_MS
                || signedAt > now + MAX_FUTURE_SKEW_MS) {
            return new IngestResult("INVALID", hash, null, "STALE_OR_FUTURE");
        }

        // ---- 5. settle ----
        Transaction tx = settlement.settle(instruction, hash, bridgeNodeId, hopCount);

        // ---- 6. report ----
        return new IngestResult(tx.getStatus().name(), hash, tx.getId(), tx.getStatus().name());
    }
}