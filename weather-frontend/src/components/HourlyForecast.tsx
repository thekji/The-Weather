import { Sun, Umbrella } from 'lucide-react'
import type {
    CurrentWeather,
    HourlyWeatherForecast,
    HourlyWeatherHour,
} from '../api/weather'
import { getWeatherArtwork } from './weatherArtwork'

type HourlyForecastProps = {
    forecast: HourlyWeatherForecast | null
    currentWeather: CurrentWeather | null
    isLoading: boolean
    error: string
    onRetry: () => void
}

function timeLabel(time: string): string {
    const timeValue = time.split('T')[1]?.slice(0, 5)
    if (!timeValue) return time

    const [rawHour, minute] = timeValue.split(':')
    const hour = Number(rawHour)
    if (!Number.isInteger(hour) || minute === undefined) return timeValue

    return `${hour % 12 || 12}:${minute} ${hour < 12 ? 'AM' : 'PM'}`
}

function currentTimeInTimezone(timezone: string): string | null {
    try {
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: timezone,
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hourCycle: 'h23',
        }).formatToParts(new Date())
        const value = (type: Intl.DateTimeFormatPartTypes) =>
            parts.find((part) => part.type === type)?.value
        const year = value('year')
        const month = value('month')
        const day = value('day')
        const hour = value('hour')
        const minute = value('minute')

        return year && month && day && hour && minute
            ? `${year}-${month}-${day}T${hour}:${minute}`
            : null
    } catch {
        return null
    }
}

function remainingHoursToday(
    forecast: HourlyWeatherForecast | null,
    currentWeather: CurrentWeather | null,
): HourlyWeatherHour[] {
    if (!forecast) return []

    const referenceTime = currentWeather?.recordedAt
        ?? currentTimeInTimezone(forecast.timezone)
    if (!referenceTime) return []

    const currentDate = referenceTime.slice(0, 10)
    const currentHour = `${referenceTime.slice(0, 13)}:00`

    return forecast.hours.filter((hour) =>
        hour.time.slice(0, 10) === currentDate && hour.time >= currentHour,
    )
}

export function HourlyForecast({
    forecast,
    currentWeather,
    isLoading,
    error,
    onRetry,
}: HourlyForecastProps) {
    const hours = remainingHoursToday(forecast, currentWeather)

    return (
        <section
            className="glass-inset mt-3.5 overflow-hidden rounded-[18px] border border-[#dce8f1] bg-[linear-gradient(145deg,rgba(255,255,255,0.78),rgba(240,247,252,0.72))]"
            aria-labelledby="hourly-forecast-title"
        >
            <div className="flex items-center justify-between gap-3 border-b border-white/30 px-4 py-3">
                <h3
                    className="m-0 text-[0.7rem] font-extrabold tracking-[0.08em] text-[var(--muted-strong)] uppercase"
                    id="hourly-forecast-title"
                >
                    Hourly forecast
                </h3>
                {forecast && (
                    <span className="truncate text-[0.66rem] font-medium text-[var(--muted)]">
                        {forecast.timezone}
                    </span>
                )}
            </div>

            {isLoading && (
                <div
                    className="grid min-h-[168px] grid-cols-6 px-2"
                    role="status"
                    aria-label="Loading hourly forecast"
                >
                    {[0, 1, 2, 3, 4, 5].map((slot) => (
                        <span
                            className="flex animate-pulse flex-col items-center justify-center gap-2.5 border-[#e5ebf0] px-2 motion-reduce:animate-none [&+span]:border-l"
                            key={slot}
                            aria-hidden="true"
                        >
                            <span className="h-2.5 w-10 rounded-full bg-[#dce6ee]" />
                            <span className="h-12 w-12 rounded-full bg-[#e8eff4]" />
                            <span className="h-4 w-8 rounded-full bg-[#dce6ee]" />
                            <span className="h-2.5 w-12 rounded-full bg-[#e3ebf1]" />
                            <span className="h-2.5 w-10 rounded-full bg-[#e3ebf1]" />
                        </span>
                    ))}
                </div>
            )}

            {!isLoading && error && (
                <div
                    className="flex min-h-[108px] items-center justify-center gap-3 px-4 py-5 text-center text-[0.76rem] font-semibold text-[#9f3340]"
                    role="alert"
                >
                    <span>{error}</span>
                    <button
                        className="shrink-0 cursor-pointer rounded-[10px] border border-[#efc7ce] bg-white/75 px-3 py-2 font-extrabold text-[#9f3340] hover:bg-white"
                        type="button"
                        onClick={onRetry}
                    >
                        Try again
                    </button>
                </div>
            )}

            {!isLoading && !error && hours.length > 0 && (
                <div className="overflow-x-auto overscroll-x-contain pb-1">
                    <div
                        className="grid min-w-max auto-cols-[144px] grid-flow-col px-2"
                        role="list"
                        aria-label="Remaining hourly weather for today"
                    >
                        {hours.map((hour) => {
                            const rainChance = Math.round(hour.precipitationProbabilityPercent)

                            return (
                                <div
                                    className="grid min-h-[168px] grid-rows-[auto_58px_auto_auto] place-items-center gap-1 border-white/30 px-2 py-3.5 text-center [&+div]:border-l"
                                    key={hour.time}
                                    role="listitem"
                                    aria-label={`${timeLabel(hour.time)}, ${hour.condition}, ${Math.round(hour.temperatureCelsius)} degrees Celsius, ${rainChance} percent chance of rain, UV index ${hour.uvIndex}, clear sky UV index ${hour.uvIndexClearSky}`}
                                >
                                    <time
                                        className="text-[0.68rem] font-bold text-[var(--muted-strong)]"
                                        dateTime={hour.time}
                                    >
                                        {timeLabel(hour.time)}
                                    </time>
                                    <img
                                        className="block h-[54px] w-[54px] object-contain drop-shadow-[0_8px_8px_rgba(45,101,148,0.12)]"
                                        src={getWeatherArtwork(hour)}
                                        alt=""
                                        aria-hidden="true"
                                    />
                                    <strong className="text-[1.12rem] leading-none font-extrabold text-[var(--ink)]">
                                        {Math.round(hour.temperatureCelsius)}°
                                    </strong>
                                    <span className="mt-1 grid gap-1 text-[0.62rem] leading-none font-bold">
                                        <span className="inline-flex items-center justify-center gap-1 whitespace-nowrap text-[var(--muted-strong)]">
                                            <Umbrella className="h-3 w-3 text-[var(--accent)]" aria-hidden="true" />
                                            {rainChance}%
                                        </span>
                                        <span className="inline-flex items-center justify-center gap-1 text-[var(--muted-strong)]">
                                            <Sun className="h-3 w-3 text-[var(--footer-icon)]" aria-hidden="true" />
                                            UV {hour.uvIndex} (clouds)
                                        </span>
                                        <span className="inline-flex items-center justify-center gap-1 whitespace-nowrap text-[#d94b5c]">
                                            <Sun className="h-3 w-3" aria-hidden="true" />
                                            Clear-sky UV {hour.uvIndexClearSky}
                                        </span>
                                    </span>
                                </div>
                            )
                        })}
                    </div>
                </div>
            )}

            {!isLoading && !error && forecast && hours.length === 0 && (
                <p className="m-0 px-4 py-6 text-center text-[0.76rem] font-semibold text-[var(--muted)]">
                    No hourly forecasts remain for today.
                </p>
            )}
        </section>
    )
}
