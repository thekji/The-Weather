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
    windSpeedKilometresPerHour: number
    windDirectionDegrees: number
    weatherCode: number
    condition: string
    daytime: boolean
}

type CurrentWeatherCoordinates = {
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
        && typeof weather.windSpeedKilometresPerHour === 'number'
        && typeof weather.windDirectionDegrees === 'number'
        && typeof weather.weatherCode === 'number'
        && typeof weather.condition === 'string'
        && weather.condition.length > 0
        && typeof weather.daytime === 'boolean'
}

export async function getCurrentWeather(
    coordinates: CurrentWeatherCoordinates,
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
