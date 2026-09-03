package com.the.weather.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Backend availability endpoints")
public class SystemPingController {

    @GetMapping("/ping")
    @Operation(summary = "Check whether the backend is responding")
    @ApiResponse(responseCode = "200", description = "The backend is available")
    public PingResponse ping() {
        return new PingResponse("weather-backend", "ok");
    }

    public record PingResponse(String service, String status) {
    }
}
