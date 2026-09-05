# TheWeather Lambda functions

## Weather handlers

Deploy the same shaded JAR as two Lambda functions with these handler classes:

```text
Forecast:   com.the.weather.lambda.ForecastWeatherHandler
Historical: com.the.weather.lambda.HistoricalWeatherHandler
```

The handler accepts an API Gateway HTTP API (payload v2) event. Supported query
parameters are:

| Parameter | Required | Description |
| --- | --- | --- |
| `latitude` or `lat` | Yes | WGS84 latitude from -90 to 90 |
| `longitude` or `lon` | Yes | WGS84 longitude from -180 to 180 |
| `dateTime` | History only* | ISO local date/time, such as `2026-09-06T14:30` |
| `date` and `time` | History only* | Alternative separate ISO values, such as `2026-09-06` and `14:30` |
| `timezone` | No | IANA timezone or `auto`; defaults to `auto` |

For the forecast handler, omit all date/time parameters to get current weather.
Provide `dateTime` (or the `date` and `time` pair) to get the nearest forecast
hour. The historical handler always requires one of those date/time forms.

Example request:

```text
GET /weather?lat=10.729&lon=106.694&timezone=Asia%2FHo_Chi_Minh
GET /weather?lat=10.729&lon=106.694&dateTime=2026-09-06T14:30&timezone=Asia%2FHo_Chi_Minh
```

The forecast function returns Open-Meteo's current conditions when no date/time
is supplied, or the nearest forecast hour when a date/time is supplied. It
accepts today through 16 days ahead. The historical function always returns the
nearest archived hour and accepts 1940-01-01 through today.

Recommended API Gateway routes:

```text
GET /weather          -> ForecastWeatherHandler
GET /weather/history  -> HistoricalWeatherHandler
```

Example response:

```json
{
  "latitude": 10.729,
  "longitude": 106.694,
  "requestedAt": "2026-09-06T14:30",
  "recordedAt": "2026-09-06T14:00",
  "timezone": "Asia/Ho_Chi_Minh",
  "temperature_2m": 31.2,
  "relative_humidity_2m": 72,
  "apparent_temperature": 34.8,
  "precipitation": 0.1,
  "rain": 0.1,
  "is_day": 1,
  "wind_speed_10m": 9.1,
  "wind_direction_10m": 190,
  "weather_code": 2,
  "condition": "Partly cloudy"
}
```

Build the deployable shaded JAR with:

```text
mvn clean package
```

The artifact is written to `target/weather-lambda.jar`.

Optional environment variables:

- `OPEN_METEO_FORECAST_URL`
- `OPEN_METEO_ARCHIVE_URL`
