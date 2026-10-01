package com.kevincarrillo.dealscanner;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StorePrice(
        String store,
        double price,
        String title,
        String link,
        String thumbnail,
        @JsonProperty("major_retailer") boolean majorRetailer) {}
