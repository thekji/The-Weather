package com.the.weather.star.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.the.weather.star.dto.CreateStarRequest;
import com.the.weather.star.dto.StarredLocationResponse;
import com.the.weather.star.exception.InvalidStarRequestException;
import com.the.weather.star.model.StarredLocation;
import com.the.weather.star.repository.StarRepository;
import com.the.weather.star.service.StarService.CreateStarResult;

class StarServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-08T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryStarRepository repository = new InMemoryStarRepository();
    private final StarService service = new StarService(repository, CLOCK);

    @Test
    void createsAStarWithAuthenticatedOwnershipAndServerOwnedFields() {
        CreateStarResult result = service.create("user_123", request("location_1"));

        assertThat(result.created()).isTrue();
        assertThat(result.star()).isEqualTo(new StarredLocationResponse(
                "location_1",
                "RMIT University",
                "702 Nguyen Van Linh, Ho Chi Minh City",
                10.729,
                106.694,
                NOW,
                false));

        StarredLocation stored = repository
                .findByUserIDAndLocationID("user_123", "location_1")
                .orElseThrow();
        assertThat(stored.userID()).isEqualTo("user_123");
        assertThat(stored.locationID()).isEqualTo("location_1");
        assertThat(stored.name()).isEqualTo("RMIT University");
        assertThat(stored.address()).isEqualTo("702 Nguyen Van Linh, Ho Chi Minh City");
        assertThat(stored.latitude()).isEqualTo(10.729);
        assertThat(stored.longitude()).isEqualTo(106.694);
        assertThat(stored.starredAt()).isEqualTo(NOW);
        assertThat(stored.weatherAlertsEnabled()).isFalse();
    }

    @Test
    void duplicateCreateReturnsTheExistingStarWithoutOverwritingIt() {
        CreateStarResult first = service.create("user_123", request("location_1"));
        CreateStarResult duplicate = service.create("user_123", new CreateStarRequest(
                "location_1",
                "Client tried to replace the name",
                "Client tried to replace the address",
                -37.8,
                144.9));

        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.star()).isEqualTo(first.star());
        assertThat(repository.items).hasSize(1);
    }

    @Test
    void listsOnlyTheAuthenticatedUsersStars() {
        service.create("user_123", request("shared_location"));
        service.create("user_123", request("user_123_location"));
        service.create("user_456", request("shared_location"));

        List<StarredLocationResponse> stars = service.list("user_123");

        assertThat(stars).extracting(StarredLocationResponse::locationID)
                .containsExactlyInAnyOrder("shared_location", "user_123_location");
        assertThat(service.list("user_without_stars")).isEmpty();
    }

    @Test
    void twoUsersCanStarTheSameLocationAndCannotDeleteEachOthersItem() {
        service.create("user_123", request("shared_location"));
        service.create("user_456", request("shared_location"));

        service.delete("user_999", "shared_location");
        assertThat(repository.findByUserIDAndLocationID("user_123", "shared_location"))
                .isPresent();
        assertThat(repository.findByUserIDAndLocationID("user_456", "shared_location"))
                .isPresent();

        service.delete("user_123", "shared_location");
        assertThat(repository.findByUserIDAndLocationID("user_123", "shared_location"))
                .isEmpty();
        assertThat(repository.findByUserIDAndLocationID("user_456", "shared_location"))
                .isPresent();
    }

    @Test
    void rejectsInvalidRequestsEvenWhenCalledOutsideTheController() {
        assertThatThrownBy(() -> service.create("user_123", new CreateStarRequest(
                "location_1", "RMIT University", "Address", null, 106.694)))
                .isInstanceOf(InvalidStarRequestException.class);
        assertThatThrownBy(() -> service.create("user_123", new CreateStarRequest(
                "location_1", "RMIT University", "Address", 91.0, 106.694)))
                .isInstanceOf(InvalidStarRequestException.class);
        assertThatThrownBy(() -> service.create("user_123", new CreateStarRequest(
                "location_1", "RMIT University", "Address", 10.729, Double.NaN)))
                .isInstanceOf(InvalidStarRequestException.class);
    }

    private static CreateStarRequest request(String locationID) {
        return new CreateStarRequest(
                locationID,
                "RMIT University",
                "702 Nguyen Van Linh, Ho Chi Minh City",
                10.729,
                106.694);
    }

    private static final class InMemoryStarRepository implements StarRepository {
        private final Map<String, StarredLocation> items = new LinkedHashMap<>();

        @Override
        public List<StarredLocation> findAllByUserID(String userID) {
            return items.values().stream()
                    .filter(item -> item.userID().equals(userID))
                    .toList();
        }

        @Override
        public Optional<StarredLocation> findByUserIDAndLocationID(
                String userID,
                String locationID) {
            return Optional.ofNullable(items.get(key(userID, locationID)));
        }

        @Override
        public boolean saveIfAbsent(StarredLocation location) {
            return items.putIfAbsent(key(location.userID(), location.locationID()), location) == null;
        }

        @Override
        public void deleteByUserIDAndLocationID(String userID, String locationID) {
            items.remove(key(userID, locationID));
        }

        private static String key(String userID, String locationID) {
            return userID + "\u0000" + locationID;
        }
    }
}
