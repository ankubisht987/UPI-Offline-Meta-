package com.ankush.upi_offline_meta.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ankush.upi_offline_meta.model.MeshPacket;

import jakarta.annotation.PostConstruct;

@Service
public class MeshSimulatorService {

    private final Map<String, VirtualDevice> devices = new LinkedHashMap<>();
    private final Random random = new Random();
    private String currentBridgeId;

    @Autowired
    private DemoService demo;

    @PostConstruct
    public void init() {
        resetMesh();
    }

    public void resetMesh() {
        // First time: create all devices with no bridge set
        devices.clear();
        List<String> ids = Arrays.asList("phone-Rahul", "phone-Anuj", "phone-Himanshu", "phone-Satyam",
                "phone-Parvesh");
        for (String id : ids) {
            devices.put(id, new VirtualDevice(id, false));
        }
        rotateBridge();
    }

    public void rotateBridge() {
        List<String> ids = new ArrayList<>(devices.keySet());
        String newBridge = ids.get(random.nextInt(ids.size()));
        this.currentBridgeId = newBridge;

        // Flip: only newBridge gets internet, everyone else false
        for (Map.Entry<String, VirtualDevice> e : devices.entrySet()) {
            e.getValue().setHasInternet(e.getKey().equals(newBridge));
        }
    }

    public String getCurrentBridgeId() {
        return currentBridgeId;
    }

    public MeshPacket inject(String deviceId, String senderVpa, String receiverVpa,
            BigDecimal amount, String pin, int ttl) throws Exception {
        MeshPacket packet = demo.createPacket(senderVpa, receiverVpa, amount, pin, ttl);
        VirtualDevice device = devices.computeIfAbsent(deviceId, id -> new VirtualDevice(id, false));
        device.receive(packet);
        return packet;
    }

    public Map<String, Object> gossipOnce() {
        int transfers = 0;

        // 1. Snapshot what every phone holds RIGHT NOW
        Map<String, List<MeshPacket>> snapshot = new LinkedHashMap<>();
        devices.forEach((id, d) -> snapshot.put(id, new ArrayList<>(d.packets())));

        // 2. Every phone hands its packet to every other phone (once)
        for (var src : snapshot.entrySet()) {
            for (MeshPacket p : src.getValue()) {
                if (p.getTtl() <= 0)
                    continue; // dead packet
                for (var dst : devices.entrySet()) {
                    if (dst.getKey().equals(src.getKey()))
                        continue; // not itself
                    if (dst.getValue().has(p.getPacketId()))
                        continue;// already has it
                    dst.getValue().receive(copyWithTtl(p, p.getTtl() - 1));
                    transfers++;
                }
            }
        }
        return Map.of("transfers", transfers, "devices", snapshot());
    }

    private MeshPacket copyWithTtl(MeshPacket src, int ttl) {
        MeshPacket c = new MeshPacket();
        c.setPacketId(src.getPacketId());
        c.setTtl(ttl);
        c.setCreatedAt(src.getCreatedAt());
        c.setCiphertext(src.getCiphertext());
        return c;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        devices.values().forEach(d -> m.put(d.getDeviceId(),
                Map.of("hasInternet", d.hasInternet(), "packetCount", d.size())));
        return m;
    }

    public List<MeshPacket> collectBridgeUploads() { // used in Step 7
        List<MeshPacket> out = new ArrayList<>();
        devices.values().stream()
                .filter(VirtualDevice::hasInternet)
                .forEach(d -> out.addAll(d.packets()));
        return out;
    }

    public int clearBridge() {
        int cleared = 0;
        for (VirtualDevice d : devices.values()) {
            if (d.hasInternet()) {
                cleared += d.size();
                d.clear();
            }
        }
        return cleared;
    }
}
