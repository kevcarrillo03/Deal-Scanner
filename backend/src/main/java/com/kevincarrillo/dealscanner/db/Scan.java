package com.kevincarrillo.dealscanner.db;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "scans")
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upc")
    private Product product;

    private String deviceId;

    private Instant scannedAt;

    protected Scan() {}

    public Scan(Product product, String deviceId){
        this.product = product;
        this.deviceId = deviceId;
        this.scannedAt = Instant.now();
    }

    public Long getId(){ return id; }
    public Product getProduct(){ return product; }
    public String getDeviceId(){ return deviceId; }
    public Instant getScannedAt(){ return scannedAt; }
}
