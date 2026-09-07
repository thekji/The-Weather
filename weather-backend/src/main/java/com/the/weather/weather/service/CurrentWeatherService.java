package com.the.weather.weather.service;

import com.the.weather.weather.dto.CurrentWeatherDto;

public interface CurrentWeatherService {

    CurrentWeatherDto current(double latitude, double longitude);
}
