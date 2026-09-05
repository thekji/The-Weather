package com.the.weather.weather;

public interface CurrentWeatherService {

    CurrentWeatherDto current(double latitude, double longitude);
}
