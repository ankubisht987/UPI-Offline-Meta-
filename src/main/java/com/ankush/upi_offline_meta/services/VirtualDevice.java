package com.ankush.upi_offline_meta.services;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ankush.upi_offline_meta.model.MeshPacket;

public class VirtualDevice {
    public final String deviceId;
    private boolean hasInternet; // no longer final
    private final Map<String, MeshPacket> inbox = new LinkedHashMap<>();

    VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean hasInternet() {
        return hasInternet;
    }

    public void setHasInternet(boolean hasInternet) {
        this.hasInternet = hasInternet;
    }

    public void receive(MeshPacket p) {
        inbox.put(p.getPacketId(), p);
    }

    public boolean has(String packetId) {
        return inbox.containsKey(packetId);
    }

    public Collection<MeshPacket> packets() {
        return inbox.values();
    }

    public int size() {
        return inbox.size();
    }

    public void clear() {
        inbox.clear();
    }
}