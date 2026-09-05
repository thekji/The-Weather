package com.the.weather.location;

import java.util.List;

public interface LocationSearchService {

    List<LocationSearchResultDto> search(String query);
}
