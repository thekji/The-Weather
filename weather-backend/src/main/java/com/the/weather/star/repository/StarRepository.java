package com.the.weather.star.repository;

import java.util.List;
import java.util.Optional;

import com.the.weather.star.model.StarredLocation;

public interface StarRepository {

    List<StarredLocation> findAllByUserID(String userID);

    Optional<StarredLocation> findByUserIDAndLocationID(String userID, String locationID);

    boolean saveIfAbsent(StarredLocation location);

    void deleteByUserIDAndLocationID(String userID, String locationID);
}
