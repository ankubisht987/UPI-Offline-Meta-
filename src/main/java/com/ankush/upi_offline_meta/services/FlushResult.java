package com.ankush.upi_offline_meta.services;

import java.util.List;

public record FlushResult(
        int collected,
        int settled,
        int rejected,
        int duplicates,
        int invalid,
        List<IngestResult> results) {
}