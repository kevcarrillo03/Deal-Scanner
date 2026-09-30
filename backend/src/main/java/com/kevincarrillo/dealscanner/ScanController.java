package com.kevincarrillo.dealscanner;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/scan")

public class ScanController {

    private static final int MAX_DEVICE_ID_LENGTH = 64;

    private final ScanService scanService;

    public ScanController(ScanService scanService){
        this.scanService = scanService;
    }

    @GetMapping("/{upc}")
    public Mono<ScanResult> getProductInfo(
            @PathVariable String upc,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId){
        if (deviceId != null && (deviceId.isBlank() || deviceId.length() > MAX_DEVICE_ID_LENGTH)){
            deviceId = null;
        }
        return scanService.scan(upc, deviceId);
    }

}
