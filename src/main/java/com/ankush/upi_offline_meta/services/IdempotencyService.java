package com.ankush.upi_offline_meta.services;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    /** How long we remember a fingerprint. */
    private static final Duration TTL = Duration.ofHours(24);

    /** fingerprint -> time it was first claimed. */
    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    /**
     * Try to claim a packet.
     * 
     * @return true = first time (you may process it)
     *         false = duplicate (drop it)
     */
    public boolean claim(String packetHash) {
        long now = System.currentTimeMillis();
        return seen.putIfAbsent(packetHash, now) == null;
    }

    public boolean isSeen(String packetHash) {
        return seen.containsKey(packetHash);
    }

    public int size() {
        return seen.size();
    }

    public void clear() {
        seen.clear();
    }

    /** Forget fingerprints older than the TTL so memory doesn't grow forever. */
    @Scheduled(fixedDelay = 3_600_000) // every hour
    public void evictExpired() {
        long cutoff = System.currentTimeMillis() - TTL.toMillis();
        seen.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
