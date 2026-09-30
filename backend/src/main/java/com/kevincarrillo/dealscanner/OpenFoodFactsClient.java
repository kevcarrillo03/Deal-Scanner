package com.kevincarrillo.dealscanner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Component
public class OpenFoodFactsClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "DealScanner/1.0 (https://github.com/kevcarrillo03/Deal-Scanner)";

    private final WebClient webClient;

    public OpenFoodFactsClient(WebClient.Builder webClientBuilder){
        this.webClient = webClientBuilder
                .baseUrl("https://world.openfoodfacts.org")
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    public Mono<UpcResponse.Item> lookup(String upc){
        System.out.println("looking up upc on open food facts: " + upc);

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v2/product/{upc}.json")
                        .queryParam("fields", "product_name,brands,quantity,image_front_url")
                        .build(upc))
                .retrieve()
                .bodyToMono(OpenFoodFactsResponse.class)
                .timeout(TIMEOUT)
                .onErrorResume(WebClientResponseException.NotFound.class, error -> Mono.empty())
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error -> {
                    System.out.println("open food facts unavailable for upc " + upc + ": " + error.getMessage());
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "open food facts lookup failed for upc: " + upc);
                })
                .flatMap(response -> {
                    if (response.status() == null || response.status() != 1 || response.product() == null){
                        return Mono.empty();
                    }
                    OpenFoodFactsResponse.Product product = response.product();
                    String name = productName(product.brands(), product.productName(), product.quantity());
                    if (name == null){
                        return Mono.empty();
                    }
                    System.out.println("product found on open food facts: " + name);
                    List<String> images = isBlank(product.imageFrontUrl()) ? null : List.of(product.imageFrontUrl());
                    return Mono.just(new UpcResponse.Item(name, null, product.brands(), images, null));
                });
    }

    static String productName(String brands, String productName, String quantity){
        if (isBlank(productName)){
            return null;
        }

        List<String> parts = new ArrayList<>();
        String brand = isBlank(brands) ? null : brands.split(",")[0].trim();
        if (brand != null && !productName.toLowerCase().contains(brand.toLowerCase())){
            parts.add(brand);
        }
        parts.add(productName.trim());
        if (!isBlank(quantity)){
            parts.add(quantity.trim());
        }
        return String.join(" ", parts);
    }

    private static boolean isBlank(String value){
        return value == null || value.isBlank();
    }
}
