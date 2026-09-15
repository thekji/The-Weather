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
        <section className="min-w-0 rounded-[19px] border border-[var(--line)] bg-[var(--soft-surface)] p-3.5" aria-labelledby="daily-forecast-title">
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
                    <button className="cursor-pointer rounded-xl border border-[var(--line)] bg-[var(--surface)] px-3 py-2 font-bold transition-transform active:scale-95 text-[var(--ink)]" type="button" onClick={onRetry}>
                        Try again
                    </button>
                </div>
            )}

            {forecast && !isLoading && (
                <div className="mt-3 overflow-x-auto overscroll-x-contain pb-1">
                    <ul className="m-0 grid min-w-max auto-cols-[132px] grid-flow-col list-none gap-2 p-0" aria-label="Seven-day weather forecast">
                        {forecast.days.slice(0, 7).map((day) => {
                            const displayDate = formatDate(day.date)
                            return (
                                <li className="flex min-h-[218px] min-w-0 flex-col items-center rounded-2xl border border-[var(--line)] bg-[var(--surface)] px-3 py-3 text-center" key={day.date}>
                                    <strong className="text-[0.84rem] font-extrabold tracking-wide text-[var(--ink)]">{displayDate.weekday}</strong>
                                    <span className="mt-0.5 text-[0.66rem] font-medium italic text-[var(--muted)]">{displayDate.calendarDate}</span>
                                    <img className="my-2 size-12 object-contain drop-shadow-[0_7px_8px_rgba(45,101,148,0.16)]" src={getWeatherArtwork({ weatherCode: day.weatherCode, daytime: true })} alt={day.condition} />
                                    <div className="whitespace-nowrap text-[0.94rem] font-extrabold text-[var(--ink)] tabular-nums">
                                        {Math.round(day.maximumTemperatureCelsius)}°
                                        <span className="font-semibold text-[var(--muted)]"> / {Math.round(day.minimumTemperatureCelsius)}°</span>
                                    </div>
                                    <div className="mt-2 grid w-full gap-1.5 border-t border-white/30 pt-2 text-[0.66rem] font-semibold text-[var(--muted-strong)]">
                                        <span className="flex items-center gap-1 whitespace-nowrap"><Sunrise className="size-3.5 shrink-0 text-[#e0a20b]" aria-hidden="true" />{formatTime(day.sunrise)}</span>
                                        <span className="flex items-center gap-1 whitespace-nowrap"><Sunset className="size-3.5 shrink-0 text-[#f17359]" aria-hidden="true" />{formatTime(day.sunset)}</span>
                                        <span className="flex items-center gap-1 whitespace-nowrap"><Umbrella className="size-3.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />{Math.round(day.precipitationProbabilityMaxPercent)}%</span>
                                    </div>
                                </li>
                            )
                        })}
                    </ul>
                </div>
            )}
        </section>
    )
}
