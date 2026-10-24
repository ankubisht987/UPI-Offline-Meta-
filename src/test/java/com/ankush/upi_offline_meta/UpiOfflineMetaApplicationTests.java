package com.ankush.upi_offline_meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ankush.upi_offline_meta.crypto.HybridCryptoService;
import com.ankush.upi_offline_meta.crypto.ServerKeyHolder;
import com.ankush.upi_offline_meta.model.AccountRepository;
import com.ankush.upi_offline_meta.model.MeshPacket;
import com.ankush.upi_offline_meta.model.PaymentInstruction;
import com.ankush.upi_offline_meta.model.Transaction;
import com.ankush.upi_offline_meta.services.BridgeIngestionService;
import com.ankush.upi_offline_meta.services.DemoService;
import com.ankush.upi_offline_meta.services.FlushResult;
import com.ankush.upi_offline_meta.services.IdempotencyService;
import com.ankush.upi_offline_meta.services.IngestResult;
import com.ankush.upi_offline_meta.services.MeshFlushService;
import com.ankush.upi_offline_meta.services.MeshSimulatorService;
import com.ankush.upi_offline_meta.services.SettlementService;

// import ch.qos.logback.core.util.Duration;

@SpringBootTest
class UpiOfflineMetaApplicationTests {

    // @Test
    // void contextLoads() {
    // }
    @Autowired
    private HybridCryptoService crypto;
    @Autowired
    private ServerKeyHolder serverKey;
    @Autowired
    private DemoService demo;
    @Autowired
    private AccountRepository accountRepo;

    @Autowired
    private MeshSimulatorService mesh;

    @Autowired
    IdempotencyService idem;

    @Autowired
    private SettlementService settlement;
    @Autowired
    private BridgeIngestionService ingestion;
    @Autowired
    private MeshFlushService flusher;

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        PaymentInstruction original = new PaymentInstruction(
                new BigDecimal("123.45"), // amount
                "nonce-1", // nonce
                "abc", // pinHash
                "sahil@hdfc", // receiverVpa
                "ankush@sbi", // senderVpa
                System.currentTimeMillis() // signedAt
        );

        String ct = crypto.encrypt(original, serverKey.getPublicKey());
        PaymentInstruction back = crypto.decrypt(ct);

        assertEquals(original.getSenderVpa(), back.getSenderVpa());
        assertEquals(original.getReceiverVpa(), back.getReceiverVpa());
        assertEquals(0, original.getAmount().compareTo(back.getAmount()));
        assertEquals(original.getNonce(), back.getNonce());
    }

    @Test
    void tamperedCiphertextFails() throws Exception {
        PaymentInstruction original = new PaymentInstruction(
                new BigDecimal("50.00"), "nonce-2", "abc",
                "sahil@hdfc", "ankush@sbi", System.currentTimeMillis());

        String ct = crypto.encrypt(original, serverKey.getPublicKey());

        // Flip ONE character in the middle -> GCM tag should no longer verify
        char[] chars = ct.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = (chars[mid] == 'A') ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(Exception.class, () -> crypto.decrypt(tampered));
    }

    @Test
    void seedsAccountsAndCreatesPacket() throws Exception {
        // seeding happened at startup
        assertTrue(accountRepo.count() >= 4);

        // build a packet like the sender's phone would
        MeshPacket packet = demo.createPacket(
                "ankush@sbi", "sahil@hdfc", new BigDecimal("100.00"), "1234", 5);

        assertNotNull(packet.getPacketId());
        assertNotNull(packet.getCiphertext());

        // unlock it to prove it really holds the right payment
        PaymentInstruction inside = crypto.decrypt(packet.getCiphertext());
        assertEquals("ankush@sbi", inside.getSenderVpa());
        assertEquals("sahil@hdfc", inside.getReceiverVpa());
        assertEquals(0, new BigDecimal("100.00").compareTo(inside.getAmount()));
    }

    @Test
    void packetSpreadsAcrossMesh() throws Exception {
        mesh.resetMesh();
        mesh.inject("phone-ankush", "ankush@sbi", "sahil@hdfc",
                new BigDecimal("100.00"), "1234", 5);
        mesh.gossipOnce();
        assertEquals(5, mesh.snapshot().size());
    }

    @Test
    void firstClaimWinsSecondIsDuplicate() {
        idem.clear();
        assertTrue(idem.claim("abc"));
        assertFalse(idem.claim("abc"));
        assertTrue(idem.isSeen("abc"));
    }

    @Test
    void duplicatePacketIsRejected() {
        idem.clear();

        // first time -> allowed
        assertTrue(idem.claim("fingerprint-123"));

        // second time, same fingerprint -> duplicate
        assertFalse(idem.claim("fingerprint-123"));

        // a different packet is fine
        assertTrue(idem.claim("fingerprint-456"));
    }

    @Test
    void samePaymentIsOnlyClaimedOnce() throws Exception {
        idem.clear();

        MeshPacket packet = demo.createPacket(
                "ankush@sbi", "sahil@hdfc", new BigDecimal("100.00"), "1234", 5);
        String hash = crypto.hashCiphertext(packet.getCiphertext());

        assertTrue(idem.claim(hash), "first delivery should be accepted");
        assertFalse(idem.claim(hash), "second delivery must be rejected");
        assertTrue(idem.isSeen(hash));
    }

    @Test
    void settlesPaymentAndMovesMoney() {
        // pin known starting balances (other tests may have changed them)
        var a = accountRepo.findById("ankush@sbi").orElseThrow();
        a.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(a);
        var b = accountRepo.findById("sahil@hdfc").orElseThrow();
        b.setBalance(new BigDecimal("1000.00"));
        accountRepo.save(b);

        PaymentInstruction ins = new PaymentInstruction(
                new BigDecimal("100.00"), "nonce-s1", "abc",
                "sahil@hdfc", "ankush@sbi", Instant.now().toEpochMilli());

        Transaction tx = settlement.settle(ins, UUID.randomUUID().toString(),
                "bridge-node", 2);

        assertEquals(Transaction.Status.SETTLED, tx.getStatus());
        assertEquals(0, new BigDecimal("4900.00")
                .compareTo(accountRepo.findById("ankush@sbi").orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("1100.00")
                .compareTo(accountRepo.findById("sahil@hdfc").orElseThrow().getBalance()));
    }

    @Test
    void rejectsWhenInsufficientFunds() {
        var d = accountRepo.findById("sahil@hdfc").orElseThrow();
        d.setBalance(new BigDecimal("500.00"));
        accountRepo.save(d);

        PaymentInstruction ins = new PaymentInstruction(
                new BigDecimal("9999.00"), "nonce-s2", "abc",
                "sahil@hdfc", "sahil@hdfc", Instant.now().toEpochMilli());

        Transaction tx = settlement.settle(ins, UUID.randomUUID().toString(),
                "bridge-node", 1);

        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
        // dave's money must be untouched
        assertEquals(0, new BigDecimal("500.00")
                .compareTo(accountRepo.findById("sahil@hdfc").orElseThrow().getBalance()));
    }

    @Test
    void settlingSamePacketTwiceMovesMoneyOnce() {
        var a = accountRepo.findById("ankush@sbi").orElseThrow();
        a.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(a);
        var b = accountRepo.findById("sahil@hdfc").orElseThrow();
        b.setBalance(new BigDecimal("1000.00"));
        accountRepo.save(b);

        PaymentInstruction ins = new PaymentInstruction(
                new BigDecimal("100.00"), "nonce-s3", "abc",
                "sahil@hdfc", "ankush@sbi", Instant.now().toEpochMilli());
        String hash = UUID.randomUUID().toString();

        settlement.settle(ins, hash, "bridge-node", 2);
        settlement.settle(ins, hash, "bridge-node", 2); // duplicate!

        assertEquals(0, new BigDecimal("4900.00")
                .compareTo(accountRepo.findById("ankush@sbi").orElseThrow().getBalance()),
                "money must move exactly once");
    }

    @Test
    void rejectsNegativeAmount() {
        PaymentInstruction ins = new PaymentInstruction(
                new BigDecimal("-100.00"), "nonce-neg", "abc",
                "sahil@hdfc", "ankush@sbi", Instant.now().toEpochMilli());

        Transaction tx = settlement.settle(ins, UUID.randomUUID().toString(),
                "bridge-node", 1);

        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
    }

    @Test
    void validPacketIsSettled() throws Exception {
        idem.clear();
        var a = accountRepo.findById("ankush@sbi").orElseThrow();
        a.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(a);
        var b = accountRepo.findById("sahil@hdfc").orElseThrow();
        b.setBalance(new BigDecimal("1000.00"));
        accountRepo.save(b);

        MeshPacket p = demo.createPacket("ankush@sbi", "sahil@hdfc",
                new BigDecimal("100.00"), "1234", 5);

        IngestResult r = ingestion.ingest(p, "bridge-node", 2);

        assertEquals("SETTLED", r.outcome());
        assertNotNull(r.transactionId());
        assertEquals(0, new BigDecimal("4900.00")
                .compareTo(accountRepo.findById("ankush@sbi").orElseThrow().getBalance()));
    }

    @Test
    void duplicateUploadIsDropped() throws Exception {
        idem.clear();
        var a = accountRepo.findById("ankush@sbi").orElseThrow();
        a.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(a);

        MeshPacket p = demo.createPacket("ankush@sbi", "sahil@hdfc",
                new BigDecimal("100.00"), "1234", 5);

        assertEquals("SETTLED", ingestion.ingest(p, "bridge-node", 1).outcome());
        assertEquals("DUPLICATE_DROPPED", ingestion.ingest(p, "bridge-node",
                1).outcome());
    }

    @Test
    void stalePacketIsInvalid() throws Exception {
        idem.clear();

        PaymentInstruction old = new PaymentInstruction(
                new BigDecimal("10.00"), "nonce-old", "abc", "sahil@hdfc", "ankush@sbi",
                System.currentTimeMillis() - Duration.ofDays(2).toMillis()); // 2 days old

        MeshPacket p = new MeshPacket();
        p.setPacketId(UUID.randomUUID().toString());
        p.setTtl(5);
        p.setCreatedAt(System.currentTimeMillis());
        p.setCiphertext(crypto.encrypt(old, serverKey.getPublicKey()));

        assertEquals("INVALID", ingestion.ingest(p, "bridge-node", 1).outcome());
    }

    @Test
    void tamperedPacketIsInvalid() throws Exception {
        idem.clear();

        MeshPacket p = demo.createPacket("ankush@sbi", "sahil@hdfc",
                new BigDecimal("100.00"), "1234", 5);

        // flip one character of the ciphertext
        char[] c = p.getCiphertext().toCharArray();
        c[c.length / 2] = (c[c.length / 2] == 'A') ? 'B' : 'A';
        p.setCiphertext(new String(c));

        assertEquals("INVALID", ingestion.ingest(p, "bridge-node", 1).outcome());
    }

    @Test
    void insufficientFundsIsRejected() throws Exception {
        idem.clear();
        var d = accountRepo.findById("ankush@sbi").orElseThrow();
        d.setBalance(new BigDecimal("10.00"));
        accountRepo.save(d);

        MeshPacket p = demo.createPacket("ankush@sbi", "sahil@hdfc",
                new BigDecimal("9999.00"), "1234", 5);

        assertEquals("REJECTED", ingestion.ingest(p, "bridge-node", 1).outcome());
    }

    @Test
    void flushSettlesAndClearsBridge() throws Exception {
        idem.clear();
        mesh.resetMesh();

        var a = accountRepo.findById("ankush@sbi").orElseThrow();
        a.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(a);
        var b = accountRepo.findById("sahil@hdfc").orElseThrow();
        b.setBalance(new BigDecimal("1000.00"));
        accountRepo.save(b);

        mesh.inject("phone-ankush", "ankush@sbi", "sahil@hdfc",
                new BigDecimal("100.00"), "1234", 5);
        mesh.gossipOnce(); // bridge now holds it

        FlushResult r = flusher.flush(5);

        assertEquals(1, r.collected());
        assertEquals(1, r.settled());
        assertEquals(0, new BigDecimal("4900.00")
                .compareTo(accountRepo.findById("ankush@sbi").orElseThrow().getBalance()));
    }

    @Test
    void flushAgainDropsDuplicate() throws Exception {
        idem.clear();
        mesh.resetMesh();

        var a = accountRepo.findById("ankush@sbi").orElseThrow();
        a.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(a);

        mesh.inject("phone-ankush", "ankush@sbi", "sahil@hdfc",
                new BigDecimal("100.00"), "1234", 5);
        mesh.gossipOnce();

        assertEquals(1, flusher.flush(5).settled());

        // other phones still hold the packet; one more gossip hands it back to the
        // bridge
        mesh.gossipOnce();
        FlushResult second = flusher.flush(5);

        assertEquals(1, second.collected()); // bridge received it again
        assertEquals(1, second.duplicates()); // but it's refused
        assertEquals(0, second.settled());
        // money unchanged
        assertEquals(0, new BigDecimal("4900.00")
                .compareTo(accountRepo.findById("ankush@sbi").orElseThrow().getBalance()));
    }

    @Test
    void databaseGuardPreventsDoubleSettleAfterMemoryWipe() throws Exception {
        idem.clear();

        var ankush = accountRepo.findById("ankush@sbi").orElseThrow();
        ankush.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(ankush);

        MeshPacket packet = demo.createPacket(
                "ankush@sbi", "sahil@hdfc", new BigDecimal("100.00"), "1234", 5);

        assertEquals("SETTLED", ingestion.ingest(packet, "bridge-node",
                1).outcome());

        idem.clear(); // simulate an app restart: memory lost

        // DB still remembers the packetHash -> no second settlement
        ingestion.ingest(packet, "bridge-node", 1);

        assertEquals(0, new BigDecimal("4900.00").compareTo(
                accountRepo.findById("ankush@sbi").orElseThrow().getBalance()),
                "money must not move a second time");
    }

    @Test
    void simultaneousDeliveriesSettleExactlyOnce() throws Exception {
        idem.clear();

        var ankush = accountRepo.findById("ankush@sbi").orElseThrow();
        ankush.setBalance(new BigDecimal("5000.00"));
        accountRepo.save(ankush);

        var bob = accountRepo.findById("sahil@hdfc").orElseThrow();
        bob.setBalance(new BigDecimal("1000.00"));
        accountRepo.save(bob);

        MeshPacket packet = demo.createPacket("ankush@sbi", "sahil@hdfc", new BigDecimal("100.00"), "1234", 5);

        int N = 25;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IngestResult>> futures = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return ingestion.ingest(packet, "bridge-node", 1);
            }));
        }

        start.countDown();

        Map<String, Long> outcomes = new HashMap<>();
        for (Future<IngestResult> f : futures) {
            IngestResult r = f.get(15, TimeUnit.SECONDS);
            outcomes.merge(r.outcome(), 1L, Long::sum);
        }
        pool.shutdown();

        assertEquals(1L, outcomes.getOrDefault("SETTLED", 0L), "exactly one delivery may settle");
        assertEquals((long) (N - 1), outcomes.getOrDefault("DUPLICATE_DROPPED", 0L),
                "all others must be duplicates");

        assertEquals(0, new BigDecimal("4900.00")
                .compareTo(accountRepo.findById("ankush@sbi").orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("1100.00")
                .compareTo(accountRepo.findById("sahil@hdfc").orElseThrow().getBalance()));
    }
}
