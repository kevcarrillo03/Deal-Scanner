package com.kevincarrillo.dealscanner;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class HistoryResponses {

    private HistoryResponses() {}

    public record PricePoint(String store, double price) {}

    public record RecentScan(
            String upc,
            @JsonProperty("product_name") String productName,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("last_scanned_at") Instant lastScannedAt,
            @JsonProperty("best_price") PricePoint bestPrice) {}

    public record HistoryCheck(
            @JsonProperty("checked_at") Instant checkedAt,
            List<PricePoint> prices) {}

    public record LowestPrice(
            String store,
            double price,
            @JsonProperty("checked_at") Instant checkedAt) {}

    public record PriceHistory(
            String upc,
            @JsonProperty("product_name") String productName,
            int days,
            @JsonProperty("lowest_price") LowestPrice lowestPrice,
            List<HistoryCheck> checks) {}
}
