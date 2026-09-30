package com.kevincarrillo.dealscanner.db;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private String upc;

    private String name;

    private String imageUrl;

    private Instant firstSeenAt;

    protected Product() {}

    public Product(String upc, String name, String imageUrl){
        this.upc = upc;
        this.name = name;
        this.imageUrl = imageUrl;
        this.firstSeenAt = Instant.now();
    }

    public String getUpc(){ return upc; }
    public String getName(){ return name; }
    public String getImageUrl(){ return imageUrl; }
    public Instant getFirstSeenAt(){ return firstSeenAt; }
}
