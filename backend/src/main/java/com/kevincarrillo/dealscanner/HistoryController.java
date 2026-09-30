package com.kevincarrillo.dealscanner;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kevincarrillo.dealscanner.HistoryResponses.PriceHistory;
import com.kevincarrillo.dealscanner.HistoryResponses.RecentScan;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")

public class HistoryController {

    private static final int MAX_HISTORY_DAYS = 365;

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService){
        this.historyService = historyService;
    }

    @GetMapping("/scans/recent")
    public List<RecentScan> getRecentScans(@RequestHeader("X-Device-Id") String deviceId){
        return historyService.recentScans(deviceId);
    }

    @GetMapping("/products/{upc}/history")
    public PriceHistory getPriceHistory(@PathVariable String upc, @RequestParam(defaultValue = "30") int days){
        if (days < 1 || days > MAX_HISTORY_DAYS){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and " + MAX_HISTORY_DAYS);
        }
        return historyService.priceHistory(upc, days)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no product saved for upc: " + upc));
    }

}
