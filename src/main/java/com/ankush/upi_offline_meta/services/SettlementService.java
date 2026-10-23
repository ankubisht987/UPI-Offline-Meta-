package com.ankush.upi_offline_meta.services;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ankush.upi_offline_meta.model.Account;
import com.ankush.upi_offline_meta.model.AccountRepository;
import com.ankush.upi_offline_meta.model.PaymentInstruction;
import com.ankush.upi_offline_meta.model.Transaction;
import com.ankush.upi_offline_meta.model.TransactionRepository;

@Service
public class SettlementService {

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;

    @Transactional
    public Transaction settle(PaymentInstruction instruction,
            String packetHash,
            String bridgeNodeId,
            int hopCount) {

        // Optional<Transaction> findByPacketHash(String packetHash);
        // ---- 0. durable duplicate guard (memory guard lives in IdempotencyService)
        // ----
        var already = transactions.findByPacketHash(packetHash);
        if (already.isPresent()) {
            return already.get(); // already processed -> same result, no double move
        }

        // ---- 1. build the ledger row ----
        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(instruction.getAmount());
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt())); // Long -> Instant
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);

        // ---- 2. load accounts ----
        Account sender = accounts.findById(instruction.getSenderVpa()).orElse(null);
        Account receiver = accounts.findById(instruction.getReceiverVpa()).orElse(null);

        // ---- 3. reject: unknown account ----
        if (sender == null || receiver == null) {
            tx.setStatus(Transaction.Status.REJECTED);
            return transactions.save(tx);
        }

        // ---- 3.5 reject: invalid amount (zero or negative) ----
        BigDecimal amount = instruction.getAmount();
        if (amount == null || amount.signum() <= 0) {
            tx.setStatus(Transaction.Status.REJECTED);
            return transactions.save(tx);
        }

        // ---- 4. reject: insufficient funds ----
        if (sender.getBalance().compareTo(amount) < 0) {
            tx.setStatus(Transaction.Status.REJECTED);
            return transactions.save(tx);
        }

        // ---- 5. move the money ----
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        // ---- 6. mark settled ----
        tx.setStatus(Transaction.Status.SETTLED);
        return transactions.save(tx);
    }
}