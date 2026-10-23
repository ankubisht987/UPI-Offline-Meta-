package com.ankush.upi_offline_meta.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ankush.upi_offline_meta.model.MeshPacket;

@Service
public class MeshFlushService {
    @Autowired
    private MeshSimulatorService mesh;
    @Autowired
    private BridgeIngestionService ingestion;

    /**
     * @param initialTtl the ttl a packet started with (default 5).
     *                   hops = initialTtl - currentTtl.
     */
    public FlushResult flush(int initialTtl) {

        List<MeshPacket> uploads = mesh.collectBridgeUploads();

        int settled = 0, rejected = 0, duplicates = 0, invalid = 0;
        List<IngestResult> results = new ArrayList<>();

        for (MeshPacket p : uploads) {
            int hops = Math.max(0, initialTtl - p.getTtl());
            IngestResult r = ingestion.ingest(p, "bridge-node", hops);
            results.add(r);

            switch (r.outcome()) {
                case "SETTLED" -> settled++;
                case "REJECTED" -> rejected++;
                case "DUPLICATE_DROPPED" -> duplicates++;
                default -> invalid++;
            }
        }

        mesh.clearBridge(); // delivered -> remove from the bridge

        return new FlushResult(uploads.size(), settled, rejected, duplicates, invalid, results);
    }
}