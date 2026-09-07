package com.the.weather.map.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes only the browser-safe Geoapify map key. The server-side search key
 * must use a different environment variable and must never be returned here.
 */
@RestController
@RequestMapping("/api/config")
public class MapConfigController {

    private final String geoapifyMapApiKey;

    public MapConfigController(
            @Value("${geoapify.map-api-key:}") String geoapifyMapApiKey) {
        this.geoapifyMapApiKey = geoapifyMapApiKey;
    }

    @GetMapping
    public Map<String, String> config() {
        return Map.of("geoapifyMapApiKey", geoapifyMapApiKey);
    }
}
