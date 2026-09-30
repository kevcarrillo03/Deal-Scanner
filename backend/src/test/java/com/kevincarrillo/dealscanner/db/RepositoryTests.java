package com.kevincarrillo.dealscanner.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;

@SpringBootTest(properties = "spring.docker.compose.skip.in-tests=false")
class RepositoryTests {

    private static final String TEST_UPC = "TEST-00000001";
    private static final String TEST_DEVICE = "test-device";

    @Autowired private ProductRepository productRepository;
    @Autowired private PriceCheckRepository priceCheckRepository;
    @Autowired private ScanRepository scanRepository;

    private Product product;

    @BeforeEach
    void saveProduct(){
        productRepository.deleteById(TEST_UPC);
        product = productRepository.save(new Product(TEST_UPC, "Test Chips", "https://example.com/chips.png"));
    }

    @AfterEach
    void deleteProduct(){
        productRepository.deleteById(TEST_UPC);
    }

    @Test
    void latestCheckLoadsItsSnapshotsCheapestFirst(){
        PriceCheck check = new PriceCheck(product, Instant.now());
        check.addSnapshot(new PriceSnapshot("Walgreens", new BigDecimal("4.99"), "Chips", "https://walgreens.com", null));
        check.addSnapshot(new PriceSnapshot("Target", new BigDecimal("4.19"), "Chips", "https://target.com", null));
        priceCheckRepository.save(check);

        PriceCheck latest = priceCheckRepository.findFirstByProductUpcOrderByCheckedAtDesc(TEST_UPC).orElseThrow();

        List<PriceSnapshot> snapshots = latest.getSnapshots();
        assertEquals(2, snapshots.size());
        assertEquals("Target", snapshots.get(0).getStore());
        assertEquals(new BigDecimal("4.19"), snapshots.get(0).getPrice());
        assertEquals("Walgreens", snapshots.get(1).getStore());
    }

    @Test
    void latestCheckIsTheNewestOne(){
        Instant now = Instant.now();
        priceCheckRepository.save(new PriceCheck(product, now.minus(Duration.ofDays(2))));
        PriceCheck newest = priceCheckRepository.save(new PriceCheck(product, now));

        PriceCheck latest = priceCheckRepository.findFirstByProductUpcOrderByCheckedAtDesc(TEST_UPC).orElseThrow();

        assertEquals(newest.getId(), latest.getId());
        assertTrue(latest.getSnapshots().isEmpty());
    }

    @Test
    void historyOnlyIncludesChecksSinceTheGivenTime(){
        Instant now = Instant.now();
        priceCheckRepository.save(new PriceCheck(product, now.minus(Duration.ofDays(40))));
        priceCheckRepository.save(new PriceCheck(product, now.minus(Duration.ofDays(3))));
        priceCheckRepository.save(new PriceCheck(product, now.minus(Duration.ofDays(1))));

        List<PriceCheck> history = priceCheckRepository
                .findByProductUpcAndCheckedAtAfterOrderByCheckedAtDesc(TEST_UPC, now.minus(Duration.ofDays(30)));

        assertEquals(2, history.size());
        assertTrue(history.get(0).getCheckedAt().isAfter(history.get(1).getCheckedAt()));
    }

    @Test
    void recentProductScansListEachProductOnceAtItsLatestScan(){
        scanRepository.save(new Scan(product, TEST_DEVICE));
        Scan newest = scanRepository.save(new Scan(product, TEST_DEVICE));
        scanRepository.save(new Scan(product, "some-other-device"));

        List<ScanRepository.RecentProductScan> recent = scanRepository.findRecentProductScans(TEST_DEVICE, Limit.of(20));

        assertEquals(1, recent.size());
        assertEquals(TEST_UPC, recent.get(0).getUpc());
        assertEquals(newest.getScannedAt().truncatedTo(ChronoUnit.MICROS), recent.get(0).getLastScannedAt());
    }
}
