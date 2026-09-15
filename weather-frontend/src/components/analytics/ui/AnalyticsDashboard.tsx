import {
    BarChart3,
    FileText,
    Gauge,
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
    const width = maximumCount === 0 ? 0 : (item.count / maximumCount) * 100
    const barStyle = { width: `${width}%` } satisfies CSSProperties
    const postLabel = item.count === 1 ? 'post' : 'posts'

    return (
        <div
            className="grid grid-cols-[28px_minmax(0,1fr)_minmax(46px,auto)] items-center gap-3 max-[430px]:gap-2"
            role="img"
            aria-label={`${item.rating} star rating: ${item.count} ${postLabel}`}
        >
            <span className="inline-flex items-center justify-center gap-1 text-sm font-extrabold text-[var(--secondary-ink)]" aria-hidden="true">
                {item.rating}
                <Star className="size-3 fill-current text-amber-400" />
            </span>
            <span className="h-3 overflow-hidden rounded-full bg-[var(--soft-surface)]" aria-hidden="true">
                <span
                    className={`block h-full rounded-full bg-gradient-to-r from-[#1596f5] to-[#71c7fa] transition-[width] duration-500 motion-reduce:transition-none ${item.count > 0 ? 'min-w-1.5' : ''}`}
                    style={barStyle}
                />
            </span>
            <span className="text-right text-sm font-bold tabular-nums text-[var(--ink)]" aria-hidden="true">
                {item.count.toLocaleString()}
            </span>
        </div>
    )
}

function SummaryContent({ summary }: { summary: AnalyticsSummary }) {
    const maximumRatingCount = Math.max(
        0,
        ...summary.ratingDistribution.map((item) => item.count),
    )
    const panelClassName = 'day-starred-glass rounded-3xl border border-[var(--line)] bg-[var(--surface)] p-6 shadow-sm max-[560px]:rounded-2xl max-[560px]:p-4'

    return (
        <div className="grid gap-5 pb-1">
            <div className="grid grid-cols-2 gap-5 max-[680px]:grid-cols-1 max-[680px]:gap-3">
                <article className={`${panelClassName} flex min-h-36 items-center gap-5`} aria-labelledby="total-posts-title">
                    <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]" aria-hidden="true">
                        <FileText className="size-6" />
                    </span>
                    <div>
                        <h2 className="m-0 text-sm font-bold text-[var(--secondary-ink)]" id="total-posts-title">Total Posts</h2>
                        <p className="mt-2 mb-0 text-[clamp(2rem,4vw,3rem)] leading-none font-extrabold tracking-tight tabular-nums text-[var(--ink)]">
                            {summary.totalPosts.toLocaleString()}
                        </p>
                    </div>
                </article>

                <article className={`${panelClassName} flex min-h-36 items-center gap-5`} aria-labelledby="average-accuracy-title">
                    <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-amber-400/15 text-amber-500" aria-hidden="true">
                        <Gauge className="size-7" />
                    </span>
                    <div>
                        <h2 className="m-0 text-sm font-bold text-[var(--secondary-ink)]" id="average-accuracy-title">Average Weather Accuracy</h2>
                        <p className="mt-2 mb-0 text-[clamp(2rem,4vw,3rem)] leading-none font-extrabold tracking-tight tabular-nums text-[var(--ink)]">
                            {summary.averageWeatherAccuracy === null
                                ? 'N/A'
                                : <>{summary.averageWeatherAccuracy.toFixed(2)} <small className="text-base font-bold text-[var(--muted)]">/ 5</small></>}
                        </p>
                    </div>
                </article>
            </div>

            <div className="grid grid-cols-[minmax(0,1.2fr)_minmax(300px,0.8fr)] gap-5 max-[900px]:grid-cols-1 max-[900px]:gap-3">
                <section className={panelClassName} aria-labelledby="rating-distribution-title">
                    <div className="mb-6 flex items-center gap-3">
                        <span className="grid size-9 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]" aria-hidden="true">
                            <BarChart3 className="size-5" />
                        </span>
                        <div>
                            <h2 className="m-0 text-base font-bold text-[var(--ink)]" id="rating-distribution-title">Weather Accuracy Distribution</h2>
                            <p className="mt-1 mb-0 text-xs text-[var(--muted)]">Community ratings from 1 to 5.</p>
                        </div>
                    </div>
                    <div className="grid gap-4">
                        {summary.ratingDistribution.map((item) => (
                            <DistributionRow item={item} maximumCount={maximumRatingCount} key={item.rating} />
                        ))}
                    </div>
                </section>

                <section className={panelClassName} aria-labelledby="top-locations-title">
                    <div className="mb-5 flex items-center gap-3">
                        <span className="grid size-9 place-items-center rounded-xl bg-pink-400/15 text-pink-500" aria-hidden="true">
                            <MapPin className="size-5" />
                        </span>
                        <div>
                            <h2 className="m-0 text-base font-bold text-[var(--ink)]" id="top-locations-title">Most Active Locations</h2>
                            <p className="mt-1 mb-0 text-xs text-[var(--muted)]">Places with the most posts.</p>
                        </div>
                    </div>

                    {summary.topLocations.length > 0 ? (
                        <ol className="m-0 grid list-none gap-2 p-0">
                            {summary.topLocations.map((location, index) => (
                                <li className="grid grid-cols-[32px_minmax(0,1fr)_auto] items-center gap-3 rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] px-3.5 py-3" key={`${location.locationName}-${index}`}>
                                    <span className="grid size-8 place-items-center rounded-xl bg-[var(--surface)] text-xs font-extrabold text-[var(--accent)]" aria-label={`Rank ${index + 1}`}>{index + 1}</span>
                                    <span className="min-w-0 truncate text-sm font-bold text-[var(--ink)]">{location.locationName}</span>
                                    <span className="text-xs font-bold whitespace-nowrap text-[var(--muted)]">
                                        <strong className="text-sm tabular-nums text-[var(--ink)]">{location.postCount.toLocaleString()}</strong> {location.postCount === 1 ? 'post' : 'posts'}
                                    </span>
                                </li>
                            ))}
                        </ol>
                    ) : (
                        <p className="m-0 rounded-2xl border border-dashed border-[var(--line)] bg-[var(--soft-surface)] px-4 py-8 text-center text-sm font-medium text-[var(--muted)]">No location activity yet.</p>
                    )}
                </section>
            </div>
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
                    <p className="mt-[15px] mb-0 text-base leading-[1.6] italic font-medium text-[var(--muted)] max-[760px]:mt-2.5 max-[760px]:text-[0.88rem] max-[760px]:leading-[1.45]">See how the community rates weather conditions across shared places.</p>
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
