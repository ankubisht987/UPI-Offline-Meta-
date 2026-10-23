// package com.ankush.upi_offline_meta;

// import static org.junit.jupiter.api.Assertions.assertEquals;

// import java.math.BigDecimal;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.Future;
// import java.util.concurrent.TimeUnit;

// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;

// import com.ankush.upi_offline_meta.model.AccountRepository;
// import com.ankush.upi_offline_meta.model.MeshPacket;
// import com.ankush.upi_offline_meta.services.BridgeIngestionService;
// import com.ankush.upi_offline_meta.services.DemoService;
// import com.ankush.upi_offline_meta.services.IdempotencyService;
// import com.ankush.upi_offline_meta.services.IngestResult;

// @SpringBootTest
// class IdempotencyConcurrencyTest {

// private static final int DELIVERIES = 25;

// @Autowired
// private BridgeIngestionService ingestion;

// @Autowired
// private IdempotencyService idem;

// @Autowired
// private DemoService demo;

// @Autowired
// private AccountRepository accounts;

// @Test
// void simultaneousDeliveriesSettleExactlyOnce() throws Exception {
// idem.clear();

// var ankush = accounts.findById("ankush@bank").orElseThrow();
// ankush.setBalance(new BigDecimal("5000.00"));
// accounts.save(ankush);

// var bob = accounts.findById("bob@bank").orElseThrow();
// bob.setBalance(new BigDecimal("1000.00"));
// accounts.save(bob);

// MeshPacket packet = demo.createPacket("ankush@bank", "bob@bank", new
// BigDecimal("100.00"), "1234", 5);

// ExecutorService pool = Executors.newFixedThreadPool(DELIVERIES);
// CountDownLatch start = new CountDownLatch(1);
// List<Future<IngestResult>> futures = new ArrayList<>();

// for (int i = 0; i < DELIVERIES; i++) {
// futures.add(pool.submit(() -> {
// start.await();
// return ingestion.ingest(packet, "bridge-node", 1);
// }));
// }

// start.countDown();

// Map<String, Long> outcomes = new HashMap<>();
// for (Future<IngestResult> f : futures) {
// IngestResult r = f.get(15, TimeUnit.SECONDS);
// outcomes.merge(r.outcome(), 1L, Long::sum);
// }
// pool.shutdown();

// assertEquals(1L, outcomes.getOrDefault("SETTLED", 0L), "exactly one delivery
// may settle");
// assertEquals((long) (DELIVERIES - 1),
// outcomes.getOrDefault("DUPLICATE_DROPPED", 0L),
// "all others must be duplicates");

// assertEquals(0, new BigDecimal("4900.00")
// .compareTo(accounts.findById("ankush@bank").orElseThrow().getBalance()),
// "sender debited exactly once");
// assertEquals(0, new BigDecimal("1100.00")
// .compareTo(accounts.findById("bob@bank").orElseThrow().getBalance()),
// "receiver credited once");
// }
// }