package com.the.weather.location.service;

import java.util.List;

import com.the.weather.location.dto.LocationSearchResultDto;

public interface LocationSearchService {

    List<LocationSearchResultDto> search(String query);

    LocationSearchResultDto reverse(double latitude, double longitude);
}
