package com.the.weather.star.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.the.weather.star.dto.CreateStarRequest;
import com.the.weather.star.dto.StarredLocationResponse;
import com.the.weather.star.exception.InvalidStarRequestException;
import com.the.weather.star.model.StarredLocation;
import com.the.weather.star.repository.StarRepository;

@Service
public class StarService {

    private final StarRepository starRepository;
    private final Clock clock;

    public StarService(StarRepository starRepository, Clock clock) {
        this.starRepository = starRepository;
        this.clock = clock;
    }

    public List<StarredLocationResponse> list(String authenticatedUserID) {
        validateIdentity(authenticatedUserID);
        return starRepository.findAllByUserID(authenticatedUserID).stream()
                .map(StarredLocationResponse::from)
                .toList();
    }

    public CreateStarResult create(
            String authenticatedUserID,
            CreateStarRequest request) {
        validateIdentity(authenticatedUserID);
        validateRequest(request);

        StarredLocation candidate = new StarredLocation(
                authenticatedUserID,
                request.locationID(),
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                Instant.now(clock),
                false);

        if (starRepository.saveIfAbsent(candidate)) {
            return new CreateStarResult(StarredLocationResponse.from(candidate), true);
        }

        StarredLocation existing = starRepository
                .findByUserIDAndLocationID(authenticatedUserID, request.locationID())
                .orElseThrow(IllegalStateException::new);
        return new CreateStarResult(StarredLocationResponse.from(existing), false);
    }

    public void delete(String authenticatedUserID, String locationID) {
        validateIdentity(authenticatedUserID);
        if (!StringUtils.hasText(locationID)) {
            throw new InvalidStarRequestException();
        }
        starRepository.deleteByUserIDAndLocationID(authenticatedUserID, locationID);
    }

    private static void validateIdentity(String authenticatedUserID) {
        if (!StringUtils.hasText(authenticatedUserID)) {
            throw new InvalidStarRequestException();
        }
    }

    private static void validateRequest(CreateStarRequest request) {
        if (request == null
                || !StringUtils.hasText(request.locationID())
                || !StringUtils.hasText(request.name())
                || !StringUtils.hasText(request.address())
                || !isCoordinate(request.latitude(), -90, 90)
                || !isCoordinate(request.longitude(), -180, 180)) {
            throw new InvalidStarRequestException();
        }
    }

    private static boolean isCoordinate(Double value, double minimum, double maximum) {
        return value != null
                && Double.isFinite(value)
                && value >= minimum
                && value <= maximum;
    }

    public record CreateStarResult(StarredLocationResponse star, boolean created) {
    }
}
