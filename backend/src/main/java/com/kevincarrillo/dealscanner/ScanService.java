package com.kevincarrillo.dealscanner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ScanService {

    private static final Duration PRICE_MAX_AGE = Duration.ofHours(12);

    private final WebClient webClient;
    private final PriceService priceService;
    private final ScanStore scanStore;

    private final AsyncCache<String, ScanResult> scanCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfter(Expiry.creating((String upc, ScanResult result) -> timeUntilStale(result)))
            .buildAsync();

    public ScanService(WebClient.Builder webClientBuilder, PriceService priceService, ScanStore scanStore){
        this.webClient = webClientBuilder.baseUrl("https://api.upcitemdb.com/prod/trial").build();
        this.priceService = priceService;
        this.scanStore = scanStore;
    }

    public Mono<ScanResult> scan(String upc, String deviceId){
        if (scanCache.getIfPresent(upc) != null){
            System.out.println("cache hit for upc: " + upc);
        }

        return Mono.fromFuture(() -> scanCache.get(upc, (key, executor) -> lookup(key).toFuture()), true)
                .flatMap(result -> blocking(() -> {
                            scanStore.recordScan(upc, deviceId);
                            return result;
                        })
                        .onErrorResume(error -> {
                            System.out.println("couldn't record scan for upc " + upc + ": " + error.getMessage());
                            return Mono.just(result);
                        }));
    }

    private Mono<ScanResult> lookup(String upc){
        return blocking(() -> scanStore.findFreshResult(upc, PRICE_MAX_AGE))
                .onErrorResume(error -> {
                    System.out.println("couldn't read saved prices for upc " + upc + ": " + error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .flatMap(saved -> {
                    if (saved.isPresent()){
                        System.out.println("prices from database for upc: " + upc);
                        return Mono.just(saved.get());
                    }
                    return lookupAndSave(upc);
                });
    }

    private Mono<ScanResult> lookupAndSave(String upc){
        return findItem(upc).flatMap(item -> priceService.findPrices(item)
                .map(prices -> new ScanResult(upc, item.title(), prices, Instant.now()))
                .flatMap(result -> blocking(() -> {
                            scanStore.saveLookup(result, firstImage(item));
                            return result;
                        })
                        .onErrorResume(error -> {
                            System.out.println("couldn't save prices for upc " + upc + ": " + error.getMessage());
                            return Mono.just(result);
                        })));
    }

    private Mono<UpcResponse.Item> findItem(String upc){
        return fetchFromUpcItemDb(upc)
                .onErrorResume(
                        error -> error instanceof ResponseStatusException status && status.getStatusCode() != HttpStatus.NOT_FOUND,
                        error -> blocking(() -> scanStore.findProduct(upc))
                                .onErrorResume(dbError -> Mono.just(Optional.empty()))
                                .flatMap(saved -> {
                                    if (saved.isEmpty()) return Mono.error(error);
                                    System.out.println("upcitemdb unavailable, using saved product for upc: " + upc);
                                    String imageUrl = saved.get().getImageUrl();
                                    return Mono.just(new UpcResponse.Item(
                                            saved.get().getName(), null, null, imageUrl == null ? null : List.of(imageUrl), null));
                                }));
    }

    private Mono<UpcResponse.Item> fetchFromUpcItemDb(String upc){
        System.out.println("looking up upc: " + upc);

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/lookup").queryParam("upc", upc).build())
                .retrieve()
                .bodyToMono(UpcResponse.class)
                .onErrorMap(WebClientResponseException.class, error -> {
                    System.out.println("upcitemdb error " + error.getStatusCode() + " for upc: " + upc);
                    if (error.getStatusCode().value() == 429){
                        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "lookup limit reached, try again later");
                    }
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "lookup failed for upc: " + upc);
                })
                .flatMap(response -> {
                    if (response.items() == null || response.items().isEmpty()){
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found: " + upc));
                    }
                    UpcResponse.Item item = response.items().get(0);
                    System.out.println("product found: " + item.title());
                    return Mono.just(item);
                });
    }

    private static String firstImage(UpcResponse.Item item){
        return (item.images() != null && !item.images().isEmpty()) ? item.images().get(0) : null;
    }

    private static Duration timeUntilStale(ScanResult result){
        Duration remaining = Duration.between(Instant.now(), result.fetchedAt().plus(PRICE_MAX_AGE));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private static <T> Mono<T> blocking(Callable<T> call){
        return Mono.fromCallable(call).subscribeOn(Schedulers.boundedElastic());
    }
}
