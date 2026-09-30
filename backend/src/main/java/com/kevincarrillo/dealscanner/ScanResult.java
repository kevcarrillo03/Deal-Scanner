package com.kevincarrillo.dealscanner;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScanResult(
        String upc,
        @JsonProperty("product_name") String productName,
        List<StorePrice> prices,
        @JsonProperty("fetched_at") Instant fetchedAt) {}
