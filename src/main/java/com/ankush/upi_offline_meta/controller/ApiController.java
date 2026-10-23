package com.ankush.upi_offline_meta.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ankush.upi_offline_meta.crypto.ServerKeyHolder;
import com.ankush.upi_offline_meta.model.Account;
import com.ankush.upi_offline_meta.model.AccountRepository;
import com.ankush.upi_offline_meta.model.MeshPacket;
import com.ankush.upi_offline_meta.model.Transaction;
import com.ankush.upi_offline_meta.model.TransactionRepository;
import com.ankush.upi_offline_meta.services.BridgeIngestionService;
import com.ankush.upi_offline_meta.services.FlushResult;
import com.ankush.upi_offline_meta.services.IngestResult;
import com.ankush.upi_offline_meta.services.MeshFlushService;
import com.ankush.upi_offline_meta.services.FlushResult;
import com.ankush.upi_offline_meta.services.MeshSimulatorService;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private ServerKeyHolder serverKey;
    @Autowired
    private MeshSimulatorService mesh;
    @Autowired
    private BridgeIngestionService ingestion;
    @Autowired
    private MeshFlushService flusher;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;

    @GetMapping("/server-key")
    public Map<String, String> getServerPublicKey() {
        return Map.of("publicKey", serverKey.getPublicKeyBase64(),
                "algorithm", "RSA-2048 / OAEP-SHA256",
                "hybridScheme", "RSA-OAEP encrypts an AES-256-GCM session key");
    }

    @PostMapping("/demo/send")
    public Map<String, Object> demoSend(
            @RequestParam(defaultValue = "phone-ankush") String fromDevice,
            @RequestParam String sender,
            @RequestParam String receiver,
            @RequestParam String amount,
            @RequestParam(defaultValue = "1234") String pin,
            @RequestParam(defaultValue = "5") int ttl) throws Exception {
        MeshPacket p = mesh.inject(fromDevice, sender, receiver,
                new BigDecimal(amount), pin, ttl);
        return Map.of("packetId", p.getPacketId(), "ttl", p.getTtl(),
                "ciphertextLength", p.getCiphertext().length());
    }

    @PostMapping("/mesh/gossip")
    public Map<String, Object> gossip() {
        return mesh.gossipOnce();
    }

    @GetMapping("/mesh/state")
    public Map<String, Object> meshState() {
        return mesh.snapshot();
    }

    @PostMapping("/bridge/ingest")
    public IngestResult ingest(@RequestBody MeshPacket packet,
            @RequestParam(defaultValue = "bridge-node") String bridgeNodeId,
            @RequestParam(defaultValue = "0") int hopCount) {
        return ingestion.ingest(packet, bridgeNodeId, hopCount);
    }

    @PostMapping("/mesh/flush")
    public FlushResult flush(@RequestParam(defaultValue = "5") int initialTtl) {
        return flusher.flush(initialTtl);
    }

    @GetMapping("/accounts")
    public List<Account> accounts() {
        return accounts.findAll();
    }

    @GetMapping("/transactions")
    public List<Transaction> recentTransactions() {
        return transactions.findTop20ByOrderByIdDesc();
    }

    @PostMapping("/mesh/reset")
    public Map<String, Object> resetMesh() {
        mesh.resetMesh();
        return mesh.snapshot();
    }

}
