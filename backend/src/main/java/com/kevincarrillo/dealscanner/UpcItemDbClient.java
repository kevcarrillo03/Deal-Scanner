package com.kevincarrillo.dealscanner;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Component
public class UpcItemDbClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public UpcItemDbClient(WebClient.Builder webClientBuilder){
        this.webClient = webClientBuilder.baseUrl("https://api.upcitemdb.com/prod/trial").build();
    }

    public Mono<UpcResponse.Item> lookup(String upc){
        System.out.println("looking up upc on upcitemdb: " + upc);

        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/lookup").queryParam("upc", upc).build())
                .retrieve()
                .bodyToMono(UpcResponse.class)
                .timeout(TIMEOUT)
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error -> {
                    if (error instanceof WebClientResponseException response){
                        System.out.println("upcitemdb error " + response.getStatusCode() + " for upc: " + upc);
                        if (response.getStatusCode().value() == 429){
                            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "lookup limit reached, try again later");
                        }
                    } else {
                        System.out.println("upcitemdb unavailable for upc " + upc + ": " + error.getMessage());
                    }
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "lookup failed for upc: " + upc);
                })
                .flatMap(response -> {
                    if (response.items() == null || response.items().isEmpty()){
                        return Mono.empty();
                    }
                    UpcResponse.Item item = response.items().get(0);
                    System.out.println("product found on upcitemdb: " + item.title());
                    return Mono.just(item);
                });
    }
}
