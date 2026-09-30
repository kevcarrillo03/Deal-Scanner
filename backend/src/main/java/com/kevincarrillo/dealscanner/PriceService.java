package com.kevincarrillo.dealscanner;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Service
public class PriceService {

    private static final Map<String, String> RETAILERS = new LinkedHashMap<>();
    static {
        RETAILERS.put("walmart", "Walmart");
        RETAILERS.put("target", "Target");
        RETAILERS.put("bestbuy", "Best Buy");
        RETAILERS.put("amazon", "Amazon");
        RETAILERS.put("costco", "Costco");
        RETAILERS.put("samsclub", "Sam's Club");
        RETAILERS.put("kroger", "Kroger");
        RETAILERS.put("walgreens", "Walgreens");
        RETAILERS.put("cvs", "CVS");
    }

    private static final Duration MAX_OFFER_AGE = Duration.ofDays(90);

    private final WebClient webClient;
    private final String apiKey;

    public PriceService(WebClient.Builder webClientBuilder, @Value("${serpapi.api-key}") String apiKey){
        this.webClient = webClientBuilder.baseUrl("https://serpapi.com").build();
        this.apiKey = apiKey;
    }

    public Mono<List<StorePrice>> findPrices(UpcResponse.Item item){
        List<StorePrice> offerPrices = offerPrices(item);

        return searchGoogleShopping(item.title())
                .onErrorResume(error -> {
                    System.out.println("price search failed: " + error.getMessage());
                    return Mono.just(List.of());
                })
                .map(shoppingPrices -> {
                    List<StorePrice> allPrices = new ArrayList<>(offerPrices);
                    allPrices.addAll(shoppingPrices);
                    return cheapestPerStore(allPrices);
                });
    }

    private List<StorePrice> offerPrices(UpcResponse.Item item){
        if (item.offers() == null) return List.of();

        String thumbnail = (item.images() != null && !item.images().isEmpty()) ? item.images().get(0) : null;
        Instant oldestAllowed = Instant.now().minus(MAX_OFFER_AGE);

        List<StorePrice> prices = new ArrayList<>();
        for (UpcResponse.Offer offer : item.offers()){
            String store = matchRetailer(offer.merchant());
            if (store == null) store = matchRetailer(offer.domain());
            if (store == null || offer.price() == null || offer.price() <= 0) continue;
            if (offer.updatedT() == null || Instant.ofEpochSecond(offer.updatedT()).isBefore(oldestAllowed)) continue;

            prices.add(new StorePrice(store, offer.price(), offer.title(), offer.link(), thumbnail));
        }
        return prices;
    }

    private Mono<List<StorePrice>> searchGoogleShopping(String productName){
        if (apiKey == null || apiKey.isBlank()){
            System.out.println("SERPAPI_API_KEY is not set, skipping price search");
            return Mono.just(List.of());
        }

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search.json")
                        .queryParam("engine", "google_shopping")
                        .queryParam("q", productName)
                        .queryParam("gl", "us")
                        .queryParam("hl", "en")
                        .queryParam("api_key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(SerpApiResponse.class)
                .map(response -> {
                    if (response.error() != null){
                        System.out.println("serpapi error: " + response.error());
                    }
                    if (response.shoppingResults() == null){
                        return List.<StorePrice>of();
                    }

                    List<StorePrice> prices = new ArrayList<>();
                    for (SerpApiResponse.ShoppingResult result : response.shoppingResults()){
                        String store = matchRetailer(result.source());
                        if (store == null || result.extractedPrice() == null) continue;

                        prices.add(new StorePrice(
                                store, result.extractedPrice(), result.title(), result.productLink(), result.thumbnail()));
                    }
                    return prices;
                });
    }

    private List<StorePrice> cheapestPerStore(List<StorePrice> prices){
        Map<String, StorePrice> cheapestByStore = new LinkedHashMap<>();
        for (StorePrice price : prices){
            StorePrice current = cheapestByStore.get(price.store());
            if (current == null || price.price() < current.price()){
                cheapestByStore.put(price.store(), price);
            }
        }

        return cheapestByStore.values().stream()
                .sorted(Comparator.comparingDouble(StorePrice::price))
                .toList();
    }

    private String matchRetailer(String name){
        if (name == null) return null;
        String simpleName = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (Map.Entry<String, String> retailer : RETAILERS.entrySet()){
            if (simpleName.startsWith(retailer.getKey())) return retailer.getValue();
        }
        return null;
    }
}
