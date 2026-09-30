package com.kevincarrillo.dealscanner;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpcResponse(String code, List<Item> items) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String title, String description, String brand, List<String> images, List<Offer> offers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Offer(
            String merchant,
            String domain,
            String title,
            Double price,
            String link,
            @JsonProperty("updated_t") Long updatedT) {}
}
