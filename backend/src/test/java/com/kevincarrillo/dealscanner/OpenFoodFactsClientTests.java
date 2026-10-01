package com.kevincarrillo.dealscanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OpenFoodFactsClientTests {

    @Test
    void addsTheBrandInFrontOfTheName(){
        assertEquals("Lay's Baked Original", OpenFoodFactsClient.productName("Lay's", "Baked Original", ""));
    }

    @Test
    void skipsTheBrandWhenTheNameAlreadyHasIt(){
        assertEquals("Diet Coke Soft Drink 144 fl oz", OpenFoodFactsClient.productName("Coke", "Diet Coke Soft Drink", "144 fl oz"));
    }

    @Test
    void usesOnlyTheFirstOfSeveralBrands(){
        assertEquals("Hydration LLC Life Wtr 33.8 oz", OpenFoodFactsClient.productName("Hydration LLC, LFE Water", "Life Wtr", "33.8 oz"));
    }

    @Test
    void worksWithoutABrandOrQuantity(){
        assertEquals("Baked Original", OpenFoodFactsClient.productName(null, "Baked Original", null));
    }

    @Test
    void hasNoNameWhenTheProductNameIsMissing(){
        assertNull(OpenFoodFactsClient.productName("Lay's", " ", "6.25 oz"));
    }
}
