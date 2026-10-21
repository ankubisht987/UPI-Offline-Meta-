package com.ankush.upi_offline_meta.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ServerKeyHolder {
    private static final Logger log = LoggerFactory.getLogger(ServerKeyHolder.class);

    private KeyPair keyPair;

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
        log.info("Server key pair generated successfully.",
                getPublicKeyBase64().substring(0, 32) + "...");

    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public String getPublicKeyBase64() {
        return java.util.Base64.getEncoder().encodeToString(getPublicKey().getEncoded());
    }
}
