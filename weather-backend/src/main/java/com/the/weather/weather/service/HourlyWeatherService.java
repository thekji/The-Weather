package com.the.weather.weather.service;

import com.the.weather.weather.dto.HourlyWeatherDto;

public interface HourlyWeatherService {

    HourlyWeatherDto hourly(double latitude, double longitude);
}
