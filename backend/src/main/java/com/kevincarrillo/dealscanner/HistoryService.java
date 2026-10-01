package com.kevincarrillo.dealscanner;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kevincarrillo.dealscanner.HistoryResponses.HistoryCheck;
import com.kevincarrillo.dealscanner.HistoryResponses.LowestPrice;
import com.kevincarrillo.dealscanner.HistoryResponses.PriceHistory;
import com.kevincarrillo.dealscanner.HistoryResponses.PricePoint;
import com.kevincarrillo.dealscanner.HistoryResponses.RecentScan;
import com.kevincarrillo.dealscanner.db.PriceCheck;
import com.kevincarrillo.dealscanner.db.PriceCheckRepository;
import com.kevincarrillo.dealscanner.db.PriceSnapshot;
import com.kevincarrillo.dealscanner.db.Product;
import com.kevincarrillo.dealscanner.db.ProductRepository;
import com.kevincarrillo.dealscanner.db.ScanRepository;
import com.kevincarrillo.dealscanner.db.ScanRepository.RecentProductScan;

@Service
public class HistoryService {

    private static final int RECENT_SCANS_LIMIT = 20;

    private final ProductRepository productRepository;
    private final PriceCheckRepository priceCheckRepository;
    private final ScanRepository scanRepository;

    public HistoryService(ProductRepository productRepository, PriceCheckRepository priceCheckRepository, ScanRepository scanRepository){
        this.productRepository = productRepository;
        this.priceCheckRepository = priceCheckRepository;
        this.scanRepository = scanRepository;
    }

    @Transactional(readOnly = true)
    public List<RecentScan> recentScans(String deviceId){
        List<RecentProductScan> recent = scanRepository.findRecentProductScans(deviceId, Limit.of(RECENT_SCANS_LIMIT));

        Map<String, Product> productsByUpc = productRepository.findAllById(recent.stream().map(RecentProductScan::getUpc).toList())
                .stream()
                .collect(Collectors.toMap(Product::getUpc, Function.identity()));

        return recent.stream()
                .map(scan -> {
                    Product product = productsByUpc.get(scan.getUpc());
                    PricePoint bestPrice = priceCheckRepository.findFirstByProductUpcOrderByCheckedAtDesc(scan.getUpc())
                            .flatMap(check -> majorRetailerPrices(check).findFirst())
                            .map(this::toPricePoint)
                            .orElse(null);
                    return new RecentScan(scan.getUpc(), product.getName(), product.getImageUrl(), scan.getLastScannedAt(), bestPrice);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PriceHistory> priceHistory(String upc, int days){
        return productRepository.findById(upc).map(product -> {
            Instant since = Instant.now().minus(Duration.ofDays(days));
            List<PriceCheck> checks = priceCheckRepository.findByProductUpcAndCheckedAtAfterOrderByCheckedAtDesc(upc, since);

            List<HistoryCheck> history = checks.stream()
                    .map(check -> new HistoryCheck(check.getCheckedAt(), majorRetailerPrices(check).map(this::toPricePoint).toList()))
                    .toList();

            LowestPrice lowest = checks.stream()
                    .flatMap(this::majorRetailerPrices)
                    .min(Comparator.comparing(PriceSnapshot::getPrice))
                    .map(snapshot -> new LowestPrice(
                            snapshot.getStore(), snapshot.getPrice().doubleValue(), snapshot.getPriceCheck().getCheckedAt()))
                    .orElse(null);

            return new PriceHistory(upc, product.getName(), days, lowest, history);
        });
    }

    private Stream<PriceSnapshot> majorRetailerPrices(PriceCheck check){
        return check.getSnapshots().stream().filter(PriceSnapshot::isMajorRetailer);
    }

    private PricePoint toPricePoint(PriceSnapshot snapshot){
        return new PricePoint(snapshot.getStore(), snapshot.getPrice().doubleValue());
    }
}
