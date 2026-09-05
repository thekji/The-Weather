package com.the.weather.location;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeoapifyLocationClient implements LocationSearchService {

    private static final int RESULT_LIMIT = 10;

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public GeoapifyLocationClient(
            @Value("${geoapify.base-url:https://api.geoapify.com}") String baseUrl,
            @Value("${geoapify.search-api-key:${geoapify.map-api-key:}}") String apiKey) {
        this(RestClient.builder(), baseUrl, apiKey);
    }

    GeoapifyLocationClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<LocationSearchResultDto> search(String query) {
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Geoapify search is not configured");
        }

        try {
            GeoapifyResponse response = restClient.get()
                    .uri(
                            "/v1/geocode/search?text={query}&format=json&limit={limit}&apiKey={apiKey}",
                            query,
                            RESULT_LIMIT,
                            apiKey)
                    .retrieve()
                    .body(GeoapifyResponse.class);

            if (response == null || response.results() == null) {
                return List.of();
            }

            return response.results().stream()
                    .filter(Objects::nonNull)
                    .filter(GeoapifyLocationClient::hasValidCoordinates)
                    .map(GeoapifyLocationClient::toDto)
                    .filter(result -> StringUtils.hasText(result.name())
                            && StringUtils.hasText(result.address()))
                    .distinct()
                    .toList();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Geoapify search is temporarily unavailable",
                    exception);
        }
    }

    private static boolean hasValidCoordinates(GeoapifyResult result) {
        return result.lon() != null
                && result.lat() != null
                && Double.isFinite(result.lon())
                && Double.isFinite(result.lat())
                && result.lon() >= -180
                && result.lon() <= 180
                && result.lat() >= -90
                && result.lat() <= 90;
    }

    private static LocationSearchResultDto toDto(GeoapifyResult result) {
        String name = firstNonBlank(
                result.name(),
                result.addressLine1(),
                result.city(),
                result.formatted());
        String address = firstNonBlank(result.formatted(), result.addressLine2(), name);

        return new LocationSearchResultDto(
                name,
                address,
                result.lat(),
                result.lon());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeoapifyResponse(List<GeoapifyResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeoapifyResult(
            String name,
            String formatted,
            @JsonProperty("address_line1") String addressLine1,
            @JsonProperty("address_line2") String addressLine2,
            String city,
            Double lon,
            Double lat) {
    }
}
