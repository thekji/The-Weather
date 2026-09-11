import { getAuthorizationHeaders } from './auth'

export type CurrentWeather = {
    latitude: number
    longitude: number
    recordedAt: string
    timezone: string
    temperatureCelsius: number
    apparentTemperatureCelsius: number
    relativeHumidityPercent: number
    precipitationMillimetres: number
    rainMillimetres: number
    cloudCoverPercent: number
    windSpeedKilometresPerHour: number
    windDirectionDegrees: number
    weatherCode: number
    condition: string
    daytime: boolean
}

export type HourlyWeatherHour = {
    time: string
    temperatureCelsius: number
    precipitationProbabilityPercent: number
    weatherCode: number
    condition: string
    uvIndex: number
    uvIndexClearSky: number
    daytime: boolean
}

export type HourlyWeatherForecast = {
    latitude: number
    longitude: number
    timezone: string
    hours: HourlyWeatherHour[]
}

export type DailyWeatherDay = {
    date: string
    maximumTemperatureCelsius: number
    minimumTemperatureCelsius: number
    sunrise: string
    sunset: string
    precipitationProbabilityMaxPercent: number
    weatherCode: number
    condition: string
}

export type DailyWeatherForecast = {
    latitude: number
    longitude: number
    timezone: string
    days: DailyWeatherDay[]
}

type WeatherCoordinates = {
    latitude: number
    longitude: number
}

function isCurrentWeather(value: unknown): value is CurrentWeather {
    if (typeof value !== 'object' || value === null) return false

    const weather = value as Record<string, unknown>
    return typeof weather.latitude === 'number'
        && typeof weather.longitude === 'number'
        && typeof weather.recordedAt === 'string'
        && typeof weather.timezone === 'string'
        && typeof weather.temperatureCelsius === 'number'
        && typeof weather.apparentTemperatureCelsius === 'number'
        && typeof weather.relativeHumidityPercent === 'number'
        && typeof weather.precipitationMillimetres === 'number'
        && typeof weather.rainMillimetres === 'number'
        && typeof weather.cloudCoverPercent === 'number'
        && typeof weather.windSpeedKilometresPerHour === 'number'
        && typeof weather.windDirectionDegrees === 'number'
        && typeof weather.weatherCode === 'number'
        && typeof weather.condition === 'string'
        && weather.condition.length > 0
        && typeof weather.daytime === 'boolean'
}

function isHourlyWeatherHour(value: unknown): value is HourlyWeatherHour {
    if (typeof value !== 'object' || value === null) return false

    const hour = value as Record<string, unknown>
    return typeof hour.time === 'string'
        && hour.time.length > 0
        && typeof hour.temperatureCelsius === 'number'
        && typeof hour.precipitationProbabilityPercent === 'number'
        && typeof hour.weatherCode === 'number'
        && typeof hour.condition === 'string'
        && hour.condition.length > 0
        && typeof hour.uvIndex === 'number'
        && typeof hour.uvIndexClearSky === 'number'
        && typeof hour.daytime === 'boolean'
}

function isHourlyWeatherForecast(value: unknown): value is HourlyWeatherForecast {
    if (typeof value !== 'object' || value === null) return false

    const forecast = value as Record<string, unknown>
    return typeof forecast.latitude === 'number'
        && typeof forecast.longitude === 'number'
        && typeof forecast.timezone === 'string'
        && Array.isArray(forecast.hours)
        && forecast.hours.length > 0
        && forecast.hours.every(isHourlyWeatherHour)
}

function isDailyWeatherDay(value: unknown): value is DailyWeatherDay {
    if (typeof value !== 'object' || value === null) return false

    const day = value as Record<string, unknown>
    return typeof day.date === 'string'
        && day.date.length > 0
        && typeof day.maximumTemperatureCelsius === 'number'
        && typeof day.minimumTemperatureCelsius === 'number'
        && typeof day.sunrise === 'string'
        && day.sunrise.length > 0
        && typeof day.sunset === 'string'
        && day.sunset.length > 0
        && typeof day.precipitationProbabilityMaxPercent === 'number'
        && typeof day.weatherCode === 'number'
        && typeof day.condition === 'string'
        && day.condition.length > 0
}

function isDailyWeatherForecast(value: unknown): value is DailyWeatherForecast {
    if (typeof value !== 'object' || value === null) return false

    const forecast = value as Record<string, unknown>
    return typeof forecast.latitude === 'number'
        && typeof forecast.longitude === 'number'
        && typeof forecast.timezone === 'string'
        && Array.isArray(forecast.days)
        && forecast.days.length > 0
        && forecast.days.every(isDailyWeatherDay)
}

export async function getCurrentWeather(
    coordinates: WeatherCoordinates,
    signal?: AbortSignal,
): Promise<CurrentWeather> {
    const searchParams = new URLSearchParams({
        latitude: String(coordinates.latitude),
        longitude: String(coordinates.longitude),
    })
    const response = await fetch(`/api/weather?${searchParams}`, {
        headers: {
            Accept: 'application/json',
            ...getAuthorizationHeaders(),
        },
        signal,
    })

    if (!response.ok) {
        throw new Error('Current weather could not be loaded.')
    }

    const weather: unknown = await response.json()
    if (!isCurrentWeather(weather)) {
        throw new Error('The weather service returned an invalid response.')
    }

    return weather
}

export async function getHourlyWeather(
    coordinates: WeatherCoordinates,
    signal?: AbortSignal,
): Promise<HourlyWeatherForecast> {
    const searchParams = new URLSearchParams({
        latitude: String(coordinates.latitude),
        longitude: String(coordinates.longitude),
    })
    const response = await fetch(`/api/weather/hourly?${searchParams}`, {
        headers: {
            Accept: 'application/json',
            ...getAuthorizationHeaders(),
        },
        signal,
    })

    if (!response.ok) {
        throw new Error('Hourly forecast could not be loaded.')
    }

    const forecast: unknown = await response.json()
    if (!isHourlyWeatherForecast(forecast)) {
        throw new Error('The weather service returned an invalid hourly forecast.')
    }

    return forecast
}

export async function getDailyWeather(
    coordinates: WeatherCoordinates,
    signal?: AbortSignal,
): Promise<DailyWeatherForecast> {
    const searchParams = new URLSearchParams({
        latitude: String(coordinates.latitude),
        longitude: String(coordinates.longitude),
    })
    const response = await fetch(`/api/weather/daily?${searchParams}`, {
        headers: {
            Accept: 'application/json',
            ...getAuthorizationHeaders(),
        },
        signal,
    })

    if (!response.ok) {
        throw new Error('Daily forecast could not be loaded.')
    }

    const forecast: unknown = await response.json()
    if (!isDailyWeatherForecast(forecast)) {
        throw new Error('The weather service returned an invalid daily forecast.')
    }

    return forecast
}
