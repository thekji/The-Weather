package com.the.weather.location.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.the.weather.location.dto.LocationSearchResultDto;
import com.the.weather.location.service.LocationSearchService;
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

    private static final int RESULT_LIMIT = 15;

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
        requireConfiguration();

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

    @Override
    public LocationSearchResultDto reverse(double latitude, double longitude) {
        requireConfiguration();

        try {
            GeoapifyResponse response = restClient.get()
                    .uri(
                            "/v1/geocode/reverse?lat={latitude}&lon={longitude}"
                                    + "&format=json&limit=1&apiKey={apiKey}",
                            latitude,
                            longitude,
                            apiKey)
                    .retrieve()
                    .body(GeoapifyResponse.class);

            if (response == null || response.results() == null) {
                throw locationNotFound();
            }

            return response.results().stream()
                    .filter(Objects::nonNull)
                    .filter(GeoapifyLocationClient::hasValidCoordinates)
                    .map(GeoapifyLocationClient::toDto)
                    .filter(GeoapifyLocationClient::hasDisplayDetails)
                    .findFirst()
                    .orElseThrow(GeoapifyLocationClient::locationNotFound);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Geoapify reverse geocoding is temporarily unavailable",
                    exception);
        }
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Geoapify is not configured");
        }
    }

    private static boolean hasDisplayDetails(LocationSearchResultDto result) {
        return StringUtils.hasText(result.name()) && StringUtils.hasText(result.address());
    }

    private static ResponseStatusException locationNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No location was found near these coordinates");
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
                createLocationId(result, address),
                name,
                address,
                result.lat(),
                result.lon());
    }

    private static String createLocationId(GeoapifyResult result, String address) {
        if (StringUtils.hasText(result.placeId())) {
            return "geoapify:" + result.placeId().trim();
        }

        String normalizedAddress = address.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        String identity = normalizedAddress
                + "|" + String.format(Locale.ROOT, "%.5f", result.lat())
                + "|" + String.format(Locale.ROOT, "%.5f", result.lon());

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return "fallback:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
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
            @JsonProperty("place_id") String placeId,
            @JsonProperty("result_type") String resultType,
            String name,
            String formatted,
            @JsonProperty("address_line1") String addressLine1,
            @JsonProperty("address_line2") String addressLine2,
            String city,
            Double lon,
            Double lat) {
    }
}
