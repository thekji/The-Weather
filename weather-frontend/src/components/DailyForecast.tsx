import { Sunrise, Sunset, Umbrella } from 'lucide-react'
import type { DailyWeatherForecast } from '../api/weather'
import { getWeatherArtwork } from './weatherArtwork'

type DailyForecastProps = {
    forecast: DailyWeatherForecast | null
    isLoading: boolean
    error: string
    onRetry: () => void
}

function formatDate(date: string) {
    const parsed = new Date(`${date}T00:00:00`)
    return {
        weekday: new Intl.DateTimeFormat('en', { weekday: 'short' }).format(parsed),
        calendarDate: new Intl.DateTimeFormat('en', {
            month: 'short',
            day: 'numeric',
        }).format(parsed),
    }
}

function formatTime(timestamp: string) {
    const parsed = new Date(timestamp)
    return new Intl.DateTimeFormat('en', {
        hour: 'numeric',
        minute: '2-digit',
    }).format(parsed)
}

export function DailyForecast({ forecast, isLoading, error, onRetry }: DailyForecastProps) {
    return (
        <section className="glass-inset min-w-0 rounded-[19px] border border-white/55 p-3.5" aria-labelledby="daily-forecast-title">
            <h3 className="m-0 text-[0.86rem] font-extrabold tracking-[0.06em] text-[var(--muted-strong)] uppercase" id="daily-forecast-title">
                7-day forecast
            </h3>

            {isLoading && (
                <div className="flex min-h-[260px] items-center justify-center gap-2.5 text-sm text-[var(--muted)]" role="status">
                    <span className="size-6 animate-spin rounded-full border-2 border-white/45 border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                    Loading forecast…
                </div>
            )}

            {error && !isLoading && (
                <div className="flex min-h-[220px] flex-col items-center justify-center gap-3 text-center text-sm text-[#a33c50]" role="alert">
                    <span>{error}</span>
                    <button className="glass-inset cursor-pointer rounded-xl border border-white/55 px-3 py-2 font-bold transition-transform active:scale-95" type="button" onClick={onRetry}>
                        Try again
                    </button>
                </div>
            )}

            {forecast && !isLoading && (
                <ul className="mt-3 grid list-none gap-2 p-0">
                    {forecast.days.slice(0, 7).map((day) => {
                        const displayDate = formatDate(day.date)
                        return (
                            <li className="grid min-h-[54px] grid-cols-[50px_54px_68px_90px_58px] items-center justify-between gap-0 rounded-xl border border-white/45 bg-white/15 px-2 py-1.5 max-[430px]:grid-cols-[42px_42px_minmax(0,1fr)_54px] max-[430px]:gap-1.5 max-[430px]:px-1.5" key={day.date}>
                                <div className="leading-tight">
                                    <strong className="block text-[0.78rem]">{displayDate.weekday}</strong>
                                    <span className="text-[0.7rem] text-[var(--muted)]">{displayDate.calendarDate}</span>
                                </div>
                                <img className="size-11 justify-self-center object-contain drop-shadow-[0_7px_8px_rgba(45,101,148,0.16)] max-[430px]:size-10" src={getWeatherArtwork({ weatherCode: day.weatherCode, daytime: true })} alt={day.condition} />
                                <div className="justify-self-center whitespace-nowrap text-[0.78rem] font-extrabold tabular-nums">
                                    {Math.round(day.maximumTemperatureCelsius)}°
                                    <span className="font-semibold text-[var(--muted)]"> / {Math.round(day.minimumTemperatureCelsius)}°</span>
                                </div>
                                <div className="grid gap-1 text-[0.68rem] text-[var(--muted-strong)] max-[430px]:order-5 max-[430px]:col-span-full max-[430px]:grid-cols-2 max-[430px]:justify-items-center max-[430px]:border-t max-[430px]:border-white/30 max-[430px]:pt-1.5">
                                    <span className="flex items-center gap-1 whitespace-nowrap"><Sunrise className="size-4 shrink-0 text-[#e0a20b]" aria-hidden="true" />{formatTime(day.sunrise)}</span>
                                    <span className="flex items-center gap-1 whitespace-nowrap"><Sunset className="size-4 shrink-0 text-[#f17359]" aria-hidden="true" />{formatTime(day.sunset)}</span>
                                </div>
                                <span className="flex items-center justify-self-start gap-1 whitespace-nowrap text-[0.72rem] font-bold text-[var(--muted-strong)] tabular-nums max-[430px]:order-4">
                                    <Umbrella className="size-[18px] shrink-0 text-[var(--accent)]" aria-hidden="true" />
                                    {Math.round(day.precipitationProbabilityMaxPercent)}%
                                </span>
                            </li>
                        )
                    })}
                </ul>
            )}
        </section>
    )
}
