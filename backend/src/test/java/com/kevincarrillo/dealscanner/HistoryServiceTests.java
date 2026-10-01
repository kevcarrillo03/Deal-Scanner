package com.kevincarrillo.dealscanner;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.kevincarrillo.dealscanner.HistoryResponses.PriceHistory;
import com.kevincarrillo.dealscanner.HistoryResponses.RecentScan;
import com.kevincarrillo.dealscanner.db.PriceCheck;
import com.kevincarrillo.dealscanner.db.PriceCheckRepository;
import com.kevincarrillo.dealscanner.db.PriceSnapshot;
import com.kevincarrillo.dealscanner.db.Product;
import com.kevincarrillo.dealscanner.db.ProductRepository;
import com.kevincarrillo.dealscanner.db.Scan;
import com.kevincarrillo.dealscanner.db.ScanRepository;

@SpringBootTest(properties = "spring.docker.compose.skip.in-tests=false")
class HistoryServiceTests {

    private static final String CHIPS_UPC = "TEST-00000001";
    private static final String SODA_UPC = "TEST-00000002";
    private static final String DEVICE = "test-device";

    @Autowired private HistoryService historyService;
    @Autowired private ProductRepository productRepository;
    @Autowired private PriceCheckRepository priceCheckRepository;
    @Autowired private ScanRepository scanRepository;

    private Product chips;
    private Product soda;

    @BeforeEach
    void saveProducts(){
        deleteProducts();
        chips = productRepository.save(new Product(CHIPS_UPC, "Test Chips", "https://example.com/chips.png"));
        soda = productRepository.save(new Product(SODA_UPC, "Test Soda", null));
    }

    @AfterEach
    void deleteProducts(){
        productRepository.deleteById(CHIPS_UPC);
        productRepository.deleteById(SODA_UPC);
    }

    @Test
    void recentScansAreNewestFirstWithTheirBestPrice(){
        saveCheck(chips, Instant.now(), "Walgreens", "4.99", "Target", "4.19");
        scanRepository.save(new Scan(chips, DEVICE));
        scanRepository.save(new Scan(soda, DEVICE));
        scanRepository.save(new Scan(chips, DEVICE));

        List<RecentScan> recent = historyService.recentScans(DEVICE);

        assertEquals(2, recent.size());
        assertEquals("Test Chips", recent.get(0).productName());
        assertEquals("https://example.com/chips.png", recent.get(0).imageUrl());
        assertEquals("Target", recent.get(0).bestPrice().store());
        assertEquals(4.19, recent.get(0).bestPrice().price());
        assertEquals("Test Soda", recent.get(1).productName());
        assertNull(recent.get(1).bestPrice());
    }

    @Test
    void recentScansOnlyIncludeThisDevice(){
        scanRepository.save(new Scan(chips, "some-other-device"));

        assertTrue(historyService.recentScans(DEVICE).isEmpty());
    }

    @Test
    void priceHistoryHasChecksInRangeAndTheLowestPriceSeen(){
        Instant now = Instant.now();
        saveCheck(chips, now.minus(Duration.ofDays(60)), "Walmart", "2.50");
        saveCheck(chips, now.minus(Duration.ofDays(10)), "Walmart", "3.98", "Target", "4.29");
        saveCheck(chips, now.minus(Duration.ofDays(1)), "Target", "4.19");

        PriceHistory history = historyService.priceHistory(CHIPS_UPC, 30).orElseThrow();

        assertEquals("Test Chips", history.productName());
        assertEquals(2, history.checks().size());
        assertEquals("Target", history.checks().get(0).prices().get(0).store());
        assertEquals(2, history.checks().get(1).prices().size());
        assertEquals("Walmart", history.lowestPrice().store());
        assertEquals(3.98, history.lowestPrice().price());
    }

    @Test
    void priceHistoryIsEmptyForUnknownProducts(){
        assertTrue(historyService.priceHistory("TEST-UNKNOWN", 30).isEmpty());
    }

    @Test
    void bestAndLowestPricesOnlyCountMajorRetailers(){
        saveCheck(chips, Instant.now(), "Local Liquor", "1.15", "Target", "4.19", "Local Market", "2.29");
        scanRepository.save(new Scan(chips, DEVICE));

        List<RecentScan> recent = historyService.recentScans(DEVICE);
        PriceHistory history = historyService.priceHistory(CHIPS_UPC, 30).orElseThrow();

        assertEquals("Target", recent.get(0).bestPrice().store());
        assertEquals("Target", history.lowestPrice().store());
        assertEquals(1, history.checks().get(0).prices().size());
    }

    private void saveCheck(Product product, Instant checkedAt, String... storesAndPrices){
        PriceCheck check = new PriceCheck(product, checkedAt);
        for (int i = 0; i < storesAndPrices.length; i += 2){
            String store = storesAndPrices[i];
            boolean major = !store.startsWith("Local ");
            check.addSnapshot(new PriceSnapshot(store, new BigDecimal(storesAndPrices[i + 1]), null, null, null, major));
        }
        priceCheckRepository.save(check);
    }
}
