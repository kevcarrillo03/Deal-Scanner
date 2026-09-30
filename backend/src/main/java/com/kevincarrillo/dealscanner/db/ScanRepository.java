package com.kevincarrillo.dealscanner.db;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    interface RecentProductScan {
        String getUpc();
        Instant getLastScannedAt();
    }

    @Query("""
            select s.product.upc as upc, max(s.scannedAt) as lastScannedAt
            from Scan s
            where s.deviceId = :deviceId
            group by s.product.upc
            order by max(s.scannedAt) desc
            """)
    List<RecentProductScan> findRecentProductScans(@Param("deviceId") String deviceId, Limit limit);
}
