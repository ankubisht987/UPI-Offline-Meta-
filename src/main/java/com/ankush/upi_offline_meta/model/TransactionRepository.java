package com.ankush.upi_offline_meta.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findTop20ByOrderByIdDesc();

    boolean existsByPacketHash(String packetHash);

    Optional<Transaction> findByPacketHash(String packetHash);
}
