package com.kevincarrillo.dealscanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.kevincarrillo.dealscanner.db.Product;

import reactor.core.publisher.Mono;

class ScanServiceTests {

    private static final String UPC = "028400183826";
    private static final List<StorePrice> PRICES = List.of(new StorePrice("Target", 4.19, "Chips", "https://target.com", null, true));

    private UpcItemDbClient upcItemDbClient;
    private OpenFoodFactsClient openFoodFactsClient;
    private PriceService priceService;
    private ScanStore scanStore;
    private ScanService scanService;

    @BeforeEach
    void setUp(){
        upcItemDbClient = mock(UpcItemDbClient.class);
        openFoodFactsClient = mock(OpenFoodFactsClient.class);
        priceService = mock(PriceService.class);
        scanStore = mock(ScanStore.class);
        scanService = new ScanService(upcItemDbClient, openFoodFactsClient, priceService, scanStore);

        when(scanStore.findFreshResult(anyString(), any(Duration.class))).thenReturn(Optional.empty());
        when(scanStore.findProduct(anyString())).thenReturn(Optional.empty());
        when(priceService.findPrices(any())).thenReturn(Mono.just(PRICES));
    }

    @Test
    void usesUpcItemDbWhenItFindsTheProduct(){
        when(upcItemDbClient.lookup(UPC)).thenReturn(Mono.just(item("Lay's Baked Original Potato Crisps 6.25 Ounce")));

        ScanResult result = scanService.scan(UPC, "device").block();

        assertEquals("Lay's Baked Original Potato Crisps 6.25 Ounce", result.productName());
        assertEquals(PRICES, result.prices());
        verify(openFoodFactsClient, never()).lookup(anyString());
        verify(scanStore).saveLookup(eq(result), any());
        verify(scanStore).recordScan(UPC, "device");
    }

    @Test
    void fallsBackToOpenFoodFactsWhenUpcItemDbIsRateLimited(){
        when(upcItemDbClient.lookup(UPC)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)));
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Mono.just(item("Lay's Baked Original")));

        ScanResult result = scanService.scan(UPC, "device").block();

        assertEquals("Lay's Baked Original", result.productName());
        assertEquals(PRICES, result.prices());
    }

    @Test
    void fallsBackToOpenFoodFactsWhenUpcItemDbDoesNotKnowTheProduct(){
        when(upcItemDbClient.lookup(UPC)).thenReturn(Mono.empty());
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Mono.just(item("Lay's Baked Original")));

        ScanResult result = scanService.scan(UPC, "device").block();

        assertEquals("Lay's Baked Original", result.productName());
    }

    @Test
    void fallsBackToTheSavedProductWhenBothLookupsFail(){
        when(upcItemDbClient.lookup(UPC)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)));
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY)));
        when(scanStore.findProduct(UPC)).thenReturn(Optional.of(new Product(UPC, "Saved Chips", null)));

        ScanResult result = scanService.scan(UPC, "device").block();

        assertEquals("Saved Chips", result.productName());
    }

    @Test
    void returnsTheRateLimitErrorWhenNothingElseCanIdentifyTheProduct(){
        when(upcItemDbClient.lookup(UPC)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)));
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Mono.empty());

        Throwable error = scanError();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ((ResponseStatusException) error).getStatusCode());
    }

    @Test
    void returnsNotFoundWhenNoSourceKnowsTheProduct(){
        when(upcItemDbClient.lookup(UPC)).thenReturn(Mono.empty());
        when(openFoodFactsClient.lookup(UPC)).thenReturn(Mono.empty());

        Throwable error = scanError();

        assertEquals(HttpStatus.NOT_FOUND, ((ResponseStatusException) error).getStatusCode());
    }

    @Test
    void usesSavedPricesWithoutCallingAnyLookup(){
        ScanResult saved = new ScanResult(UPC, "Saved Chips", PRICES, java.time.Instant.now());
        when(scanStore.findFreshResult(eq(UPC), any(Duration.class))).thenReturn(Optional.of(saved));

        ScanResult result = scanService.scan(UPC, "device").block();

        assertEquals(saved, result);
        verify(upcItemDbClient, never()).lookup(anyString());
        verify(openFoodFactsClient, never()).lookup(anyString());
    }

    private Throwable scanError(){
        try {
            scanService.scan(UPC, "device").block();
        } catch (RuntimeException error){
            return assertInstanceOf(ResponseStatusException.class, error);
        }
        throw new AssertionError("expected the scan to fail");
    }

    private static UpcResponse.Item item(String title){
        return new UpcResponse.Item(title, null, null, null, null);
    }
}
