package com.kevincarrillo.dealscanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PriceServiceTests {

    @Test
    void keepsListingsFromEveryStoreAndMarksMajorRetailers(){
        List<StorePrice> prices = PriceService.fromShoppingResults(List.of(
                listing("Walmart - SellerCo", 3.98),
                listing("Wal-Mart.com", 4.10),
                listing("Hy-Vee", 5.94),
                listing("Nadys Liquor", 3.88)));

        assertEquals(4, prices.size());
        assertEquals("Walmart", prices.get(0).store());
        assertTrue(prices.get(0).majorRetailer());
        assertEquals("Walmart", prices.get(1).store());
        assertEquals("Hy-Vee", prices.get(2).store());
        assertFalse(prices.get(2).majorRetailer());
        assertFalse(prices.get(3).majorRetailer());
    }

    @Test
    void groupsEbaySellersIntoOneStore(){
        List<StorePrice> prices = PriceService.fromShoppingResults(List.of(
                listing("eBay - amazin.steals.deals", 6.50),
                listing("eBay - debscollectibles", 5.25)));

        assertTrue(prices.stream().allMatch(price -> price.store().equals("eBay") && !price.majorRetailer()));
    }

    @Test
    void skipsListingsWithoutAStoreOrPrice(){
        List<StorePrice> prices = PriceService.fromShoppingResults(List.of(
                listing(null, 3.00),
                listing(" ", 3.00),
                listing("Target", null),
                listing("Target", 0.0)));

        assertTrue(prices.isEmpty());
    }

    @Test
    void cheapestPerStoreKeepsOneListingPerStoreSortedByPrice(){
        List<StorePrice> cheapest = PriceService.cheapestPerStore(PriceService.fromShoppingResults(List.of(
                listing("Walmart", 7.36),
                listing("Hy-Vee", 5.94),
                listing("Walmart", 3.98),
                listing("eBay - seller one", 6.50),
                listing("eBay - seller two", 5.25))));

        assertEquals(List.of("Walmart", "eBay", "Hy-Vee"), cheapest.stream().map(StorePrice::store).toList());
        assertEquals(3.98, cheapest.get(0).price());
        assertEquals(5.25, cheapest.get(1).price());
    }

    @Test
    void cheapestPerStoreTreatsStoreNamesCaseInsensitively(){
        List<StorePrice> cheapest = PriceService.cheapestPerStore(PriceService.fromShoppingResults(List.of(
                listing("HY-VEE", 6.10),
                listing("Hy-Vee", 5.94))));

        assertEquals(1, cheapest.size());
        assertEquals(5.94, cheapest.get(0).price());
    }

    private static SerpApiResponse.ShoppingResult listing(String source, Double price){
        return new SerpApiResponse.ShoppingResult("Lay's Baked Original", source, null, price, "https://example.com", null);
    }
}
