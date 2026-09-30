package com.kevincarrillo.dealscanner.db;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_snapshots")
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_check_id")
    private PriceCheck priceCheck;

    private String store;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private String title;

    private String link;

    private String thumbnailUrl;

    protected PriceSnapshot() {}

    public PriceSnapshot(String store, BigDecimal price, String title, String link, String thumbnailUrl){
        this.store = store;
        this.price = price;
        this.title = title;
        this.link = link;
        this.thumbnailUrl = thumbnailUrl;
    }

    void setPriceCheck(PriceCheck priceCheck){ this.priceCheck = priceCheck; }

    public Long getId(){ return id; }
    public PriceCheck getPriceCheck(){ return priceCheck; }
    public String getStore(){ return store; }
    public BigDecimal getPrice(){ return price; }
    public String getTitle(){ return title; }
    public String getLink(){ return link; }
    public String getThumbnailUrl(){ return thumbnailUrl; }
}
