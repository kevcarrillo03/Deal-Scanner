package com.kevincarrillo.dealscanner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenFoodFactsResponse(Integer status, Product product) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            @JsonProperty("product_name") String productName,
            String brands,
            String quantity,
            @JsonProperty("image_front_url") String imageFrontUrl) {}
}
