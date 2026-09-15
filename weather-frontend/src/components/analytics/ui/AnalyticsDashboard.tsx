import {
    BarChart3,
    FileText,
    Lightbulb,
    MapPin,
    RefreshCw,
    Star,
} from 'lucide-react'
import { useEffect, useRef, useState, type CSSProperties } from 'react'
import {
    getAnalyticsRefreshStatus,
    getAnalyticsSummary,
    isAnalyticsDataUnavailable,
    refreshAnalytics,
} from '../service/analyticsService'
import type { AnalyticsSummary, RatingDistribution } from '../types'

export const ANALYTICS_POLL_INTERVAL_MS = 2_000
export const ANALYTICS_MAX_POLLS = 90

function isAbortError(error: unknown): boolean {
    return typeof error === 'object'
        && error !== null
        && 'name' in error
        && error.name === 'AbortError'
}

function errorMessage(error: unknown, fallback: string): string {
    return error instanceof Error && error.message.trim() ? error.message : fallback
}

function waitForNextPoll(signal: AbortSignal): Promise<void> {
    if (signal.aborted) {
        return Promise.reject(new DOMException('The request was aborted.', 'AbortError'))
    }

    return new Promise((resolve, reject) => {
        const timeoutID = window.setTimeout(() => {
            signal.removeEventListener('abort', handleAbort)
            resolve()
        }, ANALYTICS_POLL_INTERVAL_MS)

        function handleAbort() {
            window.clearTimeout(timeoutID)
            reject(new DOMException('The request was aborted.', 'AbortError'))
        }

        signal.addEventListener('abort', handleAbort, { once: true })
    })
}

function formattedDate(value: string): string {
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
    }).format(new Date(value))
}

function DistributionRow({ item, maximumCount }: {
    item: RatingDistribution
    maximumCount: number
}) {
    const height = maximumCount === 0 ? 0 : (item.count / maximumCount) * 100
    const barStyle = { height: `${height}%` } satisfies CSSProperties
    const postLabel = item.count === 1 ? 'post' : 'posts'
    const barColors = [
        'from-[#f472b6] to-[#f9a8d4]',
        'from-[#fb923c] to-[#fdba74]',
        'from-[#fbbf24] to-[#fde68a]',
        'from-[#38bdf8] to-[#7dd3fc]',
        'from-[#168ff0] to-[#60a5fa]',
    ]

    return (
        <div
            className="grid h-full min-w-0 grid-rows-[minmax(0,1fr)_auto] gap-3"
            role="img"
            aria-label={`${item.rating} star rating: ${item.count} ${postLabel}`}
        >
            <div className="flex min-h-0 flex-col justify-end" aria-hidden="true">
                <span className="mb-2 text-center text-sm font-extrabold tabular-nums text-[var(--ink)]">
                    {item.count.toLocaleString()}
                </span>
                <span
                    className={`mx-auto block w-[min(66px,72%)] rounded-t-xl bg-gradient-to-t ${barColors[item.rating - 1]} shadow-[0_8px_20px_rgba(21,150,245,0.12)] transition-[height] duration-500 motion-reduce:transition-none ${item.count > 0 ? 'min-h-1.5' : ''}`}
                    style={barStyle}
                />
            </div>
            <span className="inline-flex items-center justify-center gap-1 text-sm font-bold text-[var(--secondary-ink)]" aria-hidden="true">
                {item.rating}
                <Star className="size-3 fill-current text-amber-400" />
            </span>
        </div>
    )
}

function AverageStars({ value }: { value: number | null }) {
    const filledStars = value === null ? 0 : Math.round(value)

    return (
        <div
            className="mt-3 flex items-center gap-1.5"
            role="img"
            aria-label={value === null ? 'No average rating available' : `${value.toFixed(2)} out of 5 stars`}
        >
            {[1, 2, 3, 4, 5].map((rating) => (
                <Star
                    className={`size-5 ${rating <= filledStars ? 'fill-amber-400 text-amber-400' : 'fill-transparent text-[var(--line)]'}`}
                    strokeWidth={2.2}
                    aria-hidden="true"
                    key={rating}
                />
            ))}
        </div>
    )
}

function SummaryContent({ summary }: { summary: AnalyticsSummary }) {
    const maximumRatingCount = Math.max(
        0,
        ...summary.ratingDistribution.map((item) => item.count),
    )
    const panelClassName = 'day-starred-glass rounded-[26px] border border-[var(--line)] bg-[var(--surface)] p-[clamp(18px,2.1vw,28px)] shadow-sm max-[560px]:rounded-2xl'
    const maximumLocationCount = Math.max(
        0,
        ...summary.topLocations.map((location) => location.postCount),
    )
    const locationColors = [
        'from-[#f472b6] to-[#f9a8d4]',
        'from-[#fb923c] to-[#fdba74]',
        'from-[#fbbf24] to-[#fde68a]',
        'from-[#38bdf8] to-[#7dd3fc]',
        'from-[#168ff0] to-[#60a5fa]',
    ]

    return (
        <div className="grid gap-5 pb-1 max-[760px]:gap-3.5">
            <div className="grid grid-cols-2 gap-5 max-[760px]:grid-cols-1 max-[760px]:gap-3.5">
                <article className={`${panelClassName} relative isolate flex min-h-40 items-center gap-6 overflow-hidden max-[430px]:items-start max-[430px]:gap-4`} aria-labelledby="total-posts-title">
                    <span className="pointer-events-none absolute -right-12 -bottom-20 -z-10 size-56 rounded-full bg-pink-400/10 blur-2xl" aria-hidden="true" />
                    <span className="grid size-16 shrink-0 place-items-center rounded-[22px] bg-gradient-to-br from-pink-300/35 to-pink-500/15 text-pink-500 ring-1 ring-pink-300/20 max-[430px]:size-12 max-[430px]:rounded-2xl" aria-hidden="true">
                        <FileText className="size-8 max-[430px]:size-6" />
                    </span>
                    <div className="min-w-0">
                        <h2 className="m-0 text-base font-extrabold text-[var(--secondary-ink)]" id="total-posts-title">Total Posts</h2>
                        <p className="mt-2 mb-0 text-[clamp(2.35rem,4vw,3.25rem)] leading-none font-extrabold tracking-tight tabular-nums text-[var(--ink)]">
                            {summary.totalPosts.toLocaleString()}
                        </p>
                        <p className="mt-3 mb-0 max-w-md text-xs leading-relaxed font-medium text-[var(--muted)]">All community weather posts shared across the platform.</p>
                    </div>
                </article>

                <article className={`${panelClassName} relative isolate flex min-h-40 items-center gap-6 overflow-hidden max-[430px]:items-start max-[430px]:gap-4`} aria-labelledby="average-accuracy-title">
                    <span className="pointer-events-none absolute -right-10 -bottom-16 -z-10 size-56 rounded-full bg-amber-300/15 blur-2xl" aria-hidden="true" />
                    <span className="grid size-16 shrink-0 place-items-center rounded-[22px] bg-gradient-to-br from-amber-200/45 to-amber-400/15 text-amber-500 ring-1 ring-amber-300/20 max-[430px]:size-12 max-[430px]:rounded-2xl" aria-hidden="true">
                        <Star className="size-8 max-[430px]:size-6" />
                    </span>
                    <div className="min-w-0">
                        <h2 className="m-0 text-base font-extrabold text-[var(--secondary-ink)]" id="average-accuracy-title">Average Weather Accuracy</h2>
                        <p className="mt-2 mb-0 text-[clamp(2.35rem,4vw,3.25rem)] leading-none font-extrabold tracking-tight tabular-nums text-[var(--ink)]">
                            {summary.averageWeatherAccuracy === null
                                ? 'N/A'
                                : <>{summary.averageWeatherAccuracy.toFixed(1)} <small className="text-lg font-bold text-[var(--muted)]">/ 5</small></>}
                        </p>
                        <AverageStars value={summary.averageWeatherAccuracy} />
                        <p className="mt-3 mb-0 max-w-md text-xs leading-relaxed font-medium text-[var(--muted)]">The community’s average accuracy rating for forecasts powered by Open-Meteo.</p>
                    </div>
                </article>
            </div>

            <div className="grid grid-cols-[minmax(0,1.15fr)_minmax(320px,0.85fr)] gap-5 max-[900px]:grid-cols-1 max-[900px]:gap-3.5">
                <section className={panelClassName} aria-labelledby="rating-distribution-title">
                    <div className="mb-5 flex items-start gap-3.5">
                        <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-pink-300/35 to-pink-500/15 text-pink-500" aria-hidden="true">
                            <BarChart3 className="size-6" />
                        </span>
                        <div>
                            <h2 className="m-0 text-lg font-extrabold text-[var(--ink)]" id="rating-distribution-title">Weather Accuracy Distribution</h2>
                            <p className="mt-1 mb-0 text-xs leading-relaxed font-medium text-[var(--muted)]">How community members rate the accuracy of our Open-Meteo forecasts.</p>
                        </div>
                    </div>
                    <div className="relative h-64 rounded-2xl border border-[var(--line)] bg-[linear-gradient(to_bottom,transparent_calc(25%-1px),var(--line)_25%,transparent_calc(25%+1px),transparent_calc(50%-1px),var(--line)_50%,transparent_calc(50%+1px),transparent_calc(75%-1px),var(--line)_75%,transparent_calc(75%+1px))] px-3 pt-3 pb-2 max-[430px]:h-56 max-[430px]:px-1.5">
                        <div className="grid h-full grid-cols-5 gap-2 border-b border-[var(--line)] max-[430px]:gap-0.5">
                        {summary.ratingDistribution.map((item) => (
                            <DistributionRow item={item} maximumCount={maximumRatingCount} key={item.rating} />
                        ))}
                        </div>
                    </div>
                </section>

                <section className={panelClassName} aria-labelledby="top-locations-title">
                    <div className="mb-5 flex items-start gap-3.5">
                        <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-pink-300/35 to-pink-500/15 text-pink-500" aria-hidden="true">
                            <MapPin className="size-6" />
                        </span>
                        <div>
                            <h2 className="m-0 text-lg font-extrabold text-[var(--ink)]" id="top-locations-title">Most Active Locations</h2>
                            <p className="mt-1 mb-0 text-xs leading-relaxed font-medium text-[var(--muted)]">Locations where the community shares the most weather reviews.</p>
                        </div>
                    </div>

                    {summary.topLocations.length > 0 ? (
                        <ol className="m-0 grid list-none gap-3 p-0">
                            {summary.topLocations.map((location, index) => (
                                <li className="grid grid-cols-[36px_minmax(0,1fr)] items-center gap-3 rounded-2xl px-1 py-1.5" key={`${location.locationName}-${index}`}>
                                    <span className={`grid size-9 place-items-center rounded-full bg-gradient-to-br ${locationColors[index % locationColors.length]} text-xs font-extrabold text-slate-900 shadow-sm`} aria-label={`Rank ${index + 1}`}>{index + 1}</span>
                                    <div className="min-w-0">
                                        <div className="mb-2 flex items-baseline justify-between gap-3">
                                            <span className="min-w-0 truncate text-sm font-extrabold text-[var(--ink)]">{location.locationName}</span>
                                            <span className="shrink-0 text-xs font-bold whitespace-nowrap text-[var(--muted)]">
                                                <strong className="text-sm tabular-nums text-[var(--ink)]">{location.postCount.toLocaleString()}</strong> {location.postCount === 1 ? 'post' : 'posts'}
                                            </span>
                                        </div>
                                        <div className="h-2 overflow-hidden rounded-full bg-[var(--soft-surface)]" aria-hidden="true">
                                            <span
                                                className={`block h-full rounded-full bg-gradient-to-r ${locationColors[index % locationColors.length]}`}
                                                style={{ width: `${maximumLocationCount === 0 ? 0 : (location.postCount / maximumLocationCount) * 100}%` }}
                                            />
                                        </div>
                                    </div>
                                </li>
                            ))}
                        </ol>
                    ) : (
                        <p className="m-0 rounded-2xl border border-dashed border-[var(--line)] bg-[var(--soft-surface)] px-4 py-8 text-center text-sm font-medium text-[var(--muted)]">No location activity yet.</p>
                    )}
                </section>
            </div>

            <aside className="day-starred-glass grid grid-cols-[48px_minmax(0,1fr)] items-center gap-4 rounded-[24px] border border-[var(--line)] bg-[var(--surface)] px-6 py-4 shadow-sm max-[560px]:grid-cols-[40px_minmax(0,1fr)] max-[560px]:gap-3 max-[560px]:rounded-2xl max-[560px]:px-4" aria-label="About these analytics">
                <span className="grid size-12 place-items-center rounded-2xl bg-gradient-to-br from-pink-300/35 to-pink-500/15 text-pink-500 max-[560px]:size-10" aria-hidden="true">
                    <Lightbulb className="size-6" />
                </span>
                <div>
                    <strong className="text-sm font-extrabold text-[var(--ink)]">Community-powered insights</strong>
                    <p className="mt-1 mb-0 text-xs leading-relaxed font-medium text-[var(--muted)]">These insights come from community weather reviews. Every shared post helps show how our forecasts perform across more locations.</p>
                </div>
            </aside>
        </div>
    )
}

export function AnalyticsDashboard() {
    const refreshControllerRef = useRef<AbortController | null>(null)
    const refreshingRef = useRef(false)
    const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
    const [isLoading, setIsLoading] = useState(true)
    const [isRefreshing, setIsRefreshing] = useState(false)
    const [hasNoData, setHasNoData] = useState(false)
    const [loadError, setLoadError] = useState('')
    const [refreshError, setRefreshError] = useState('')

    useEffect(() => {
        const controller = new AbortController()

        void getAnalyticsSummary(controller.signal)
            .then((latestSummary) => {
                if (controller.signal.aborted) return
                setSummary(latestSummary)
                setHasNoData(false)
            })
            .catch((error: unknown) => {
                if (controller.signal.aborted || isAbortError(error)) return
                if (isAnalyticsDataUnavailable(error)) {
                    setHasNoData(true)
                    return
                }
                setLoadError(errorMessage(error, 'Could not load analytics.'))
            })
            .finally(() => {
                if (!controller.signal.aborted) setIsLoading(false)
            })

        return () => {
            controller.abort()
            refreshControllerRef.current?.abort()
        }
    }, [])

    async function handleRefresh() {
        if (refreshingRef.current) return

        const controller = new AbortController()
        refreshControllerRef.current = controller
        refreshingRef.current = true
        setIsRefreshing(true)
        setRefreshError('')

        try {
            await refreshAnalytics(controller.signal)

            for (let attempt = 0; attempt < ANALYTICS_MAX_POLLS; attempt += 1) {
                await waitForNextPoll(controller.signal)
                const result = await getAnalyticsRefreshStatus(controller.signal)

                if (result.status === 'READY') {
                    const latestSummary = await getAnalyticsSummary(controller.signal)
                    setSummary(latestSummary)
                    setHasNoData(false)
                    setLoadError('')
                    return
                }

                if (result.status === 'FAILED') {
                    throw new Error('The analytics refresh failed. Please try again.')
                }
            }

            throw new Error('The analytics refresh timed out. Please try again.')
        } catch (error: unknown) {
            if (!controller.signal.aborted && !isAbortError(error)) {
                setRefreshError(errorMessage(error, 'Could not refresh analytics.'))
            }
        } finally {
            if (refreshControllerRef.current === controller) {
                refreshControllerRef.current = null
            }
            refreshingRef.current = false
            if (!controller.signal.aborted) setIsRefreshing(false)
        }
    }

    return (
        <section className="relative z-[1] mx-auto flex h-full min-h-0 w-full max-w-[1600px] flex-col animate-fade-in max-[760px]:h-auto" aria-labelledby="analytics-title">
            <header className="mb-[clamp(20px,3.5vh,34px)] flex shrink-0 items-start justify-between gap-7 max-[680px]:gap-4 max-[560px]:block max-[560px]:mb-4">
                <div className="min-w-0 flex-1">
                    <p className="mt-0 mb-[9px] w-fit text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase max-[760px]:mb-1.5 max-[760px]:text-[0.68rem]">Community insights</p>
                    <h1 className="m-0 w-fit text-[clamp(2.25rem,4vw,3.75rem)] leading-[0.98] font-extrabold tracking-[0.01em] text-[var(--ink)] [@media(max-height:720px)_and_(min-width:761px)]:text-[clamp(2.25rem,6vh,3.35rem)] max-[760px]:text-[clamp(2rem,9vw,2.65rem)]" id="analytics-title">Analytics</h1>
                    <p className="mt-[15px] mb-0 max-w-3xl text-base leading-[1.6] font-medium text-[var(--muted)] max-[760px]:mt-2.5 max-[760px]:text-[0.88rem] max-[760px]:leading-[1.45]">Explore how the community rates our weather forecasts across shared locations.</p>
                </div>

                <div className="flex shrink-0 flex-col items-end gap-2 max-[560px]:mt-4 max-[560px]:items-stretch">
                    <button
                        type="button"
                        className="inline-flex min-h-11 cursor-pointer items-center justify-center gap-2 rounded-2xl border-0 bg-[var(--action-background)] px-5 text-sm font-bold text-[var(--action-ink)] shadow-sm transition-all hover:bg-[var(--action-hover-background)] active:scale-95 focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#1596f566] disabled:cursor-wait disabled:opacity-70 disabled:active:scale-100"
                        disabled={isLoading || isRefreshing}
                        aria-busy={isLoading || isRefreshing}
                        onClick={() => void handleRefresh()}
                    >
                        <RefreshCw className={`size-4 ${isRefreshing ? 'animate-spin' : ''}`} aria-hidden="true" />
                        {isRefreshing ? 'Refreshing…' : 'Refresh Analytics'}
                    </button>
                    <p className="m-0 text-right text-xs font-medium text-[var(--muted)] max-[560px]:text-left">
                        Last updated:{' '}
                        {summary ? <time dateTime={summary.generatedAt}>{formattedDate(summary.generatedAt)}</time> : 'Not available yet'}
                    </p>
                </div>
            </header>

            <div className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto overscroll-contain pr-1 max-[760px]:flex-none max-[760px]:overflow-visible max-[760px]:pr-0">
                {isRefreshing && (
                    <p className="mb-4 mt-0 rounded-2xl border border-[var(--active-border)] bg-[var(--active-surface)] px-4 py-3 text-sm font-semibold text-[var(--active-ink)]" role="status" aria-live="polite">
                        Exporting posts and updating the analytics catalogue…
                    </p>
                )}

                {refreshError && (
                    <p className="mb-4 mt-0 rounded-2xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm font-semibold text-red-600" role="alert">{refreshError}</p>
                )}

                {summary && <SummaryContent summary={summary} />}

                {!summary && isLoading && (
                    <div className="day-starred-glass grid min-h-56 place-items-center rounded-3xl border border-[var(--line)] bg-[var(--surface)] p-8 text-center shadow-sm" role="status">
                        <div>
                            <RefreshCw className="mx-auto size-7 animate-spin text-[var(--accent)]" aria-hidden="true" />
                            <p className="mt-4 mb-0 text-sm font-semibold text-[var(--muted)]">Loading analytics…</p>
                        </div>
                    </div>
                )}

                {!summary && !isLoading && hasNoData && (
                    <div className="day-starred-glass grid min-h-56 place-items-center rounded-3xl border border-[var(--line)] bg-[var(--surface)] p-8 text-center shadow-sm">
                        <div className="max-w-md">
                            <BarChart3 className="mx-auto size-9 text-[var(--accent)]" aria-hidden="true" />
                            <h2 className="mt-4 mb-0 text-lg font-bold text-[var(--ink)]">No analytics data is available yet.</h2>
                            <p className="mt-2 mb-0 text-sm leading-relaxed text-[var(--muted)]">Refresh analytics to generate the latest data.</p>
                        </div>
                    </div>
                )}

                {!summary && !isLoading && loadError && (
                    <div className="day-starred-glass grid min-h-56 place-items-center rounded-3xl border border-red-500/25 bg-[var(--surface)] p-8 text-center shadow-sm" role="alert">
                        <div className="max-w-md">
                            <BarChart3 className="mx-auto size-9 text-red-500" aria-hidden="true" />
                            <h2 className="mt-4 mb-0 text-lg font-bold text-[var(--ink)]">Could not load analytics.</h2>
                            <p className="mt-2 mb-0 text-sm leading-relaxed text-[var(--muted)]">{loadError}</p>
                        </div>
                    </div>
                )}
            </div>
        </section>
    )
}
