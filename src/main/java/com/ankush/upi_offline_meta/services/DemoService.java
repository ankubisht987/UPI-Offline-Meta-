package com.ankush.upi_offline_meta.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ankush.upi_offline_meta.crypto.HybridCryptoService;
import com.ankush.upi_offline_meta.crypto.ServerKeyHolder;
import com.ankush.upi_offline_meta.model.Account;
import com.ankush.upi_offline_meta.model.AccountRepository;
import com.ankush.upi_offline_meta.model.MeshPacket;
import com.ankush.upi_offline_meta.model.PaymentInstruction;

import jakarta.annotation.PostConstruct;

@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    @Autowired
    private AccountRepository account;
    @Autowired
    private HybridCryptoService crypto;
    @Autowired
    private ServerKeyHolder serverkey;

    private static final Map<String, String> VPA_TO_DEVICE = Map.of(
            "ankush@sbi", "phone-ankush",
            "Rahul@hdfc", "phone-rahul",
            "aniket@pnb", "phone-aniket",
            "Shishir@sbi", "phone-shishir");

    public String getDeviceId(String vpa) {
        return VPA_TO_DEVICE.getOrDefault(vpa, "phone-" + vpa.split("@")[0]);
    }

    @PostConstruct
    public void seedAccounts() {
        if (account.count() == 0) {
            account.save(new Account("ankush@sbi", "Ankush", new BigDecimal("5000.00")));
            account.save(new Account("sahil@hdfc", "Sahil", new BigDecimal("2500.00")));
            account.save(new Account("aniket@pnb", "Aniket", new BigDecimal("2500.00")));
            account.save(new Account("shishir@sbi", "Shishir", new BigDecimal("5000.00")));
            log.info("Seeded 4 demo accounts");
        }
    }

    public MeshPacket createPacket(String senderVpa, String receiverVpa,
            BigDecimal amount, String pin, int ttl) throws Exception {

        // 1. Build the secret payment instruction
        PaymentInstruction instruction = new PaymentInstruction(
                amount,
                UUID.randomUUID().toString(), // nonce (unique per payment)
                sha256Hex(pin), // pinHash
                receiverVpa,
                senderVpa,
                Instant.now().toEpochMilli() // signedAt
        );

        // 2. Lock it with the bank's public key
        String ciphertext = crypto.encrypt(instruction, serverkey.getPublicKey());

        // 3. Wrap it in a mesh packet
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext(ciphertext);
        return packet;
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash)
            hex.append(String.format("%02x", b));
        return hex.toString();
    }

}
