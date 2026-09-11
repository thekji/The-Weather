package com.the.weather.weather.service;

import com.the.weather.weather.dto.DailyWeatherDto;

public interface DailyWeatherService {
    DailyWeatherDto daily(double latitude, double longitude);
}
