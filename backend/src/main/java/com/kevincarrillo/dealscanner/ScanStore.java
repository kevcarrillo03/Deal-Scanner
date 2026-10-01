package com.kevincarrillo.dealscanner;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kevincarrillo.dealscanner.db.PriceCheck;
import com.kevincarrillo.dealscanner.db.PriceCheckRepository;
import com.kevincarrillo.dealscanner.db.PriceSnapshot;
import com.kevincarrillo.dealscanner.db.Product;
import com.kevincarrillo.dealscanner.db.ProductRepository;
import com.kevincarrillo.dealscanner.db.Scan;
import com.kevincarrillo.dealscanner.db.ScanRepository;

@Service
public class ScanStore {

    private final ProductRepository productRepository;
    private final PriceCheckRepository priceCheckRepository;
    private final ScanRepository scanRepository;

    public ScanStore(ProductRepository productRepository, PriceCheckRepository priceCheckRepository, ScanRepository scanRepository){
        this.productRepository = productRepository;
        this.priceCheckRepository = priceCheckRepository;
        this.scanRepository = scanRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ScanResult> findFreshResult(String upc, Duration maxAge){
        Instant oldestAllowed = Instant.now().minus(maxAge);

        return priceCheckRepository.findFirstByProductUpcOrderByCheckedAtDesc(upc)
                .filter(check -> check.getCheckedAt().isAfter(oldestAllowed))
                .map(check -> new ScanResult(upc, check.getProduct().getName(), toStorePrices(check), check.getCheckedAt()));
    }

    @Transactional(readOnly = true)
    public Optional<Product> findProduct(String upc){
        return productRepository.findById(upc);
    }

    @Transactional
    public void saveLookup(ScanResult result, String imageUrl){
        Product product = productRepository.findById(result.upc())
                .orElseGet(() -> productRepository.save(new Product(result.upc(), result.productName(), imageUrl)));

        PriceCheck check = new PriceCheck(product, result.fetchedAt());
        for (StorePrice price : result.prices()){
            check.addSnapshot(new PriceSnapshot(
                    price.store(), BigDecimal.valueOf(price.price()), price.title(), price.link(), price.thumbnail(),
                    price.majorRetailer()));
        }
        priceCheckRepository.save(check);
    }

    @Transactional
    public void recordScan(String upc, String deviceId){
        scanRepository.save(new Scan(productRepository.getReferenceById(upc), deviceId));
    }

    private List<StorePrice> toStorePrices(PriceCheck check){
        return check.getSnapshots().stream()
                .map(snapshot -> new StorePrice(
                        snapshot.getStore(),
                        snapshot.getPrice().doubleValue(),
                        snapshot.getTitle(),
                        snapshot.getLink(),
                        snapshot.getThumbnailUrl(),
                        snapshot.isMajorRetailer()))
                .toList();
    }
}
