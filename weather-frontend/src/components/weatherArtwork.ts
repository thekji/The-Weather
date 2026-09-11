import clearDayIcon from '../assets/icons/weather-clear-day.png'
import clearNightIcon from '../assets/icons/weather-clear-night.png'
import fogDayIcon from '../assets/icons/weather-fog-cloudy-day.png'
import fogNightIcon from '../assets/icons/weather-fog-cloudy-night.png'
import partlyCloudyDayIcon from '../assets/icons/weather-partly-cloudy-day.png'
import partlyCloudyNightIcon from '../assets/icons/weather-partly-cloudy-night.png'
import rainDayIcon from '../assets/icons/weather-rain-moderate-day.png'
import rainHeavyDayIcon from '../assets/icons/weather-rain-heavy-day.png'
import rainHeavyNightIcon from '../assets/icons/weather-rain-heavy-night.png'
import rainNightIcon from '../assets/icons/weather-rain-moderate-night.png'
import showerDayIcon from '../assets/icons/weather-rain-showers-light-day.png'
import showerNightIcon from '../assets/icons/weather-rain-showers-light-night.png'
import snowDayIcon from '../assets/icons/weather-snow-light-day.png'
import snowHeavyDayIcon from '../assets/icons/weather-snow-heavy-day.png'
import snowHeavyNightIcon from '../assets/icons/weather-snow-heavy-night.png'
import snowNightIcon from '../assets/icons/weather-snow-light-night.png'
import thunderstormDayIcon from '../assets/icons/weather-thunderstorm-day.png'
import thunderstormNightIcon from '../assets/icons/weather-thunderstorm-night.png'
import thunderstormHeavyDayIcon from '../assets/icons/weather-thunderstorm-rain-heavy-day.png'
import thunderstormHeavyNightIcon from '../assets/icons/weather-thunderstorm-rain-heavy-night.png'

function dayOrNight(daytime: boolean, dayIcon: string, nightIcon: string): string {
    return daytime ? dayIcon : nightIcon
}

type WeatherArtworkSource = {
    daytime: boolean
    weatherCode: number
}

export function getWeatherArtwork(weather: WeatherArtworkSource | null | undefined): string {
    if (!weather) return clearDayIcon

    const { daytime, weatherCode: code } = weather

    if (code === 0) return dayOrNight(daytime, clearDayIcon, clearNightIcon)
    if (code >= 1 && code <= 3) {
        return dayOrNight(daytime, partlyCloudyDayIcon, partlyCloudyNightIcon)
    }
    if (code === 45 || code === 48) {
        return dayOrNight(daytime, fogDayIcon, fogNightIcon)
    }
    if (code >= 51 && code <= 57) {
        return dayOrNight(daytime, showerDayIcon, showerNightIcon)
    }
    if (code >= 61 && code <= 67) {
        const heavy = code === 65 || code === 67
        return heavy
            ? dayOrNight(daytime, rainHeavyDayIcon, rainHeavyNightIcon)
            : dayOrNight(daytime, rainDayIcon, rainNightIcon)
    }
    if (code >= 71 && code <= 77) {
        return code === 75
            ? dayOrNight(daytime, snowHeavyDayIcon, snowHeavyNightIcon)
            : dayOrNight(daytime, snowDayIcon, snowNightIcon)
    }
    if (code >= 80 && code <= 82) {
        return code === 82
            ? dayOrNight(daytime, rainHeavyDayIcon, rainHeavyNightIcon)
            : dayOrNight(daytime, showerDayIcon, showerNightIcon)
    }
    if (code === 85 || code === 86) {
        return code === 86
            ? dayOrNight(daytime, snowHeavyDayIcon, snowHeavyNightIcon)
            : dayOrNight(daytime, snowDayIcon, snowNightIcon)
    }
    if (code >= 95) {
        return code === 95
            ? dayOrNight(daytime, thunderstormDayIcon, thunderstormNightIcon)
            : dayOrNight(daytime, thunderstormHeavyDayIcon, thunderstormHeavyNightIcon)
    }

    return dayOrNight(daytime, partlyCloudyDayIcon, partlyCloudyNightIcon)
}
