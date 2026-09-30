package com.kevincarrillo.dealscanner;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SerpApiResponse(
        @JsonProperty("shopping_results") List<ShoppingResult> shoppingResults,
        @JsonProperty("error") String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShoppingResult(
            @JsonProperty("title") String title,
            @JsonProperty("source") String source,
            @JsonProperty("price") String price,
            @JsonProperty("extracted_price") Double extractedPrice,
            @JsonProperty("product_link") String productLink,
            @JsonProperty("thumbnail") String thumbnail) {}
}
