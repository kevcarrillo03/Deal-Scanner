package com.kevincarrillo.dealscanner.db;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_checks")
public class PriceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upc")
    private Product product;

    private Instant checkedAt;

    @OneToMany(mappedBy = "priceCheck", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("price ASC")
    private List<PriceSnapshot> snapshots = new ArrayList<>();

    protected PriceCheck() {}

    public PriceCheck(Product product, Instant checkedAt){
        this.product = product;
        this.checkedAt = checkedAt;
    }

    public void addSnapshot(PriceSnapshot snapshot){
        snapshots.add(snapshot);
        snapshot.setPriceCheck(this);
    }

    public Long getId(){ return id; }
    public Product getProduct(){ return product; }
    public Instant getCheckedAt(){ return checkedAt; }
    public List<PriceSnapshot> getSnapshots(){ return snapshots; }
}
