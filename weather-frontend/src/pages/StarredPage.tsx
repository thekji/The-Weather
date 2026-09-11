import { LocateFixed, Map as MapIcon, MapPin, RefreshCw, Star } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCurrentWeather, type CurrentWeather } from '../api/weather'
import {
    getStarredLocations,
    type StarredLocation,
    unstarLocation,
} from '../api/stars'
import { getWeatherArtwork } from '../components/weatherArtwork'
import { PageFooterNote } from '../components/PageFooterNote'

function errorMessage(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback
}

function coordinate(value: number, positive: string, negative: string): string {
    return `${Math.abs(value).toFixed(4)}° ${value >= 0 ? positive : negative}`
}

export function StarredPage() {
    const unstarRequestsRef = useRef(new Map<string, AbortController>())
    const [locations, setLocations] = useState<StarredLocation[]>([])
    const [isLoading, setIsLoading] = useState(true)
    const [loadError, setLoadError] = useState('')
    const [actionError, setActionError] = useState('')
    const [weatherByLocationID, setWeatherByLocationID] = useState<
        Record<string, CurrentWeather | null>
    >({})
    const [removingLocationIDs, setRemovingLocationIDs] = useState<Set<string>>(
        () => new Set(),
    )
    const [reloadKey, setReloadKey] = useState(0)

    useEffect(() => {
        const controller = new AbortController()

        void getStarredLocations(controller.signal)
            .then((starredLocations) => {
                if (controller.signal.aborted) return

                setLocations(starredLocations)
                setWeatherByLocationID({})

                void Promise.all(starredLocations.map(async (location) => {
                    try {
                        const weather = await getCurrentWeather({
                            latitude: location.latitude,
                            longitude: location.longitude,
                        }, controller.signal)
                        return [location.locationID, weather] as const
                    } catch {
                        return [location.locationID, null] as const
                    }
                })).then((entries) => {
                    if (!controller.signal.aborted) {
                        setWeatherByLocationID(Object.fromEntries(entries))
                    }
                })
            })
            .catch((error: unknown) => {
                if (!controller.signal.aborted) {
                    setLoadError(errorMessage(error, 'Starred places could not be loaded.'))
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setIsLoading(false)
            })

        return () => controller.abort()
    }, [reloadKey])

    useEffect(() => {
        const requests = unstarRequestsRef.current
        return () => {
            requests.forEach((controller) => controller.abort())
            requests.clear()
        }
    }, [])

    async function handleUnstar(location: StarredLocation) {
        if (unstarRequestsRef.current.has(location.locationID)) return

        const controller = new AbortController()
        unstarRequestsRef.current.set(location.locationID, controller)
        setActionError('')
        setRemovingLocationIDs((current) => {
            const next = new Set(current)
            next.add(location.locationID)
            return next
        })

        try {
            await unstarLocation(location.locationID, controller.signal)
            if (!controller.signal.aborted) {
                setLocations((current) => current.filter(
                    (item) => item.locationID !== location.locationID,
                ))
            }
        } catch (error) {
            if (!controller.signal.aborted) {
                setActionError(errorMessage(error, 'This place could not be unstarred.'))
            }
        } finally {
            if (!controller.signal.aborted) {
                setRemovingLocationIDs((current) => {
                    const next = new Set(current)
                    next.delete(location.locationID)
                    return next
                })
            }
            if (unstarRequestsRef.current.get(location.locationID) === controller) {
                unstarRequestsRef.current.delete(location.locationID)
            }
        }
    }

    function retryLoad() {
        setIsLoading(true)
        setLoadError('')
        setReloadKey((key) => key + 1)
    }

    const countLabel = isLoading || loadError
        ? 'Saved place count unavailable'
        : `${locations.length} saved ${locations.length === 1 ? 'place' : 'places'}`

    return (
        <section className="relative z-[1] mx-auto flex h-full min-h-0 w-full max-w-[1600px] flex-col max-[760px]:h-auto" aria-labelledby="starred-title">
            <header className="mb-[clamp(20px,3.5vh,34px)] flex shrink-0 items-start justify-between gap-7 max-[760px]:mb-4 max-[430px]:block">
                <div className="min-w-0 flex-1">
                    <p className="mt-0 mb-[9px] w-fit text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase max-[760px]:mb-1.5 max-[760px]:text-[0.68rem]">Saved locations</p>
                    <div className="flex items-center gap-[18px] max-[760px]:flex-wrap max-[760px]:gap-3">
                        <h1 className="m-0 w-fit text-[clamp(2.25rem,4vw,3.75rem)] leading-[0.98] font-extrabold tracking-[0.01em] text-[var(--ink)] [@media(max-height:720px)_and_(min-width:761px)]:text-[clamp(2.25rem,6vh,3.35rem)] max-[760px]:text-[clamp(2rem,9vw,2.65rem)]" id="starred-title">Starred places</h1>
                        <p className="mt-[5px] mb-0 shrink-0 rounded-full border border-[#1596f53d] bg-white/45 px-3.5 py-[9px] text-[0.78rem] font-extrabold text-[var(--accent)] backdrop-blur-[18px] backdrop-saturate-150 max-[760px]:mt-0 max-[760px]:px-3 max-[760px]:py-1.5 max-[760px]:text-[0.7rem] max-[430px]:inline-block" aria-label={countLabel}>
                            <span>{isLoading || loadError ? '—' : locations.length}</span>{' '}
                            {locations.length === 1 ? 'place' : 'places'}
                        </p>
                    </div>
                    <p className="mt-[15px] mb-0 text-base leading-[1.6] text-[var(--muted)] max-[760px]:mt-2.5 max-[760px]:text-[0.88rem] max-[760px]:leading-[1.45]">A quick look at the places you care about.</p>
                </div>
            </header>

            <div className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto overscroll-contain pr-1 max-[760px]:flex-none max-[760px]:overflow-visible max-[760px]:pr-0">
                <div className="mb-2 flex justify-start py-0.5">
                    <button
                        type="button"
                        className="scroll-glass-card grid size-10 shrink-0 cursor-pointer place-items-center rounded-xl border bg-[var(--active-surface)] text-[var(--ink)] transition-[background-color,transform] duration-150 hover:bg-[var(--active-surface-hover)] active:scale-[0.92] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559] motion-reduce:transition-none [&_svg]:size-[18px] [&_svg]:stroke-[2.2]"
                        onClick={() => window.location.reload()}
                        aria-label="Reload starred places"
                        title="Reload starred places"
                    >
                        <RefreshCw aria-hidden="true" />
                    </button>
                </div>

                {isLoading && (
                    <div className="scroll-glass-card flex min-h-[250px] flex-col items-center justify-center gap-2.5 rounded-[26px] border bg-[var(--state-glass-background)] px-6 py-9 text-center text-[var(--ink)]" role="status">
                        <span className="size-[34px] animate-spin rounded-full border-[3px] border-[#cfe8fa] border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                        <strong className="text-[1.08rem]">Loading starred places</strong>
                        <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">Finding your saved locations…</p>
                    </div>
                )}

                {!isLoading && loadError && (
                    <div className="scroll-glass-card flex min-h-[250px] flex-col items-center justify-center gap-2.5 rounded-[26px] border bg-[var(--state-glass-background)] px-6 py-9 text-center text-[var(--ink)]" role="alert">
                        <strong className="text-[1.08rem] text-[#9f3340]">Starred places unavailable</strong>
                        <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">{loadError}</p>
                        <button className="mt-2 cursor-pointer rounded-[10px] border-0 bg-[var(--accent)] px-3.5 py-[9px] text-[0.82rem] font-extrabold text-white focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559]" type="button" onClick={retryLoad}>Try again</button>
                    </div>
                )}

                {!isLoading && !loadError && locations.length === 0 && (
                    <div className="scroll-glass-card flex min-h-[250px] flex-col items-center justify-center gap-2.5 rounded-[26px] border bg-[var(--state-glass-background)] px-6 py-9 text-center text-[var(--ink)] [&>svg]:mb-[7px] [&>svg]:size-[42px] [&>svg]:fill-[var(--accent-soft)] [&>svg]:stroke-[1.8] [&>svg]:text-[var(--accent)]">
                        <Star aria-hidden="true" />
                        <strong className="text-[1.08rem]">No starred places yet.</strong>
                        <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">Choose a place on the map and select Star to save it here.</p>
                    </div>
                )}

                {!isLoading && !loadError && locations.length > 0 && (
                    <>
                        {actionError && (
                            <p className="scroll-glass-card mt-0 mb-[13px] rounded-xl border border-[var(--unstar-border)] [background:var(--unstar-background)] px-3.5 py-[11px] text-[0.82rem] font-bold text-[var(--unstar-ink)]" role="alert">{actionError}</p>
                        )}

                        <ul className="m-0 grid w-full list-none grid-cols-[repeat(auto-fit,minmax(min(100%,34rem),1fr))] gap-3.5 p-0 max-[430px]:gap-3" aria-label="Starred locations">
                            {locations.map((location) => {
                                const isRemoving = removingLocationIDs.has(location.locationID)
                                const weather = weatherByLocationID[location.locationID]

                                return (
                                    <li className="h-full" key={location.locationID}>
                                        <article className="scroll-glass-card grid h-full min-h-[210px] grid-cols-[minmax(0,1fr)_clamp(96px,22%,138px)] grid-rows-[minmax(102px,1fr)_auto] items-stretch gap-x-4 gap-y-3.5 rounded-[22px] border [background:var(--starred-card-background)] py-4 pr-4 pl-[18px] max-[760px]:min-h-[202px] max-[760px]:p-[15px] max-[430px]:min-h-[194px] max-[430px]:grid-cols-[minmax(0,1fr)_82px] max-[430px]:gap-x-2.5 max-[430px]:p-3">
                                            <div className="col-start-1 row-start-1 flex min-w-0 items-start gap-3.5 max-[430px]:gap-2.5">
                                                <div className="grid size-[90px] shrink-0 place-items-center rounded-[18px] max-[760px]:size-[82px] max-[430px]:size-14 max-[430px]:rounded-2xl" aria-hidden="true">
                                                    <img className="block size-[90px] object-contain drop-shadow-[0_12px_12px_rgba(45,101,148,0.16)] max-[760px]:size-[82px] max-[430px]:size-14" src={getWeatherArtwork(weather)} alt="" />
                                                </div>

                                                <div className="min-w-0">
                                                    <div className="flex items-center gap-2.5">
                                                        <h2 className="m-0 text-[clamp(1.05rem,1.6vw,1.32rem)] leading-tight font-extrabold tracking-[0.008em] text-[var(--ink)]">{location.name}</h2>
                                                    </div>
                                                    <p className="mt-2.5 mb-0 flex min-w-0 items-start gap-[7px] text-[0.82rem] leading-[1.5] text-[var(--muted)] max-[760px]:text-[0.76rem] [&_svg]:mt-0.5 [&_svg]:size-[15px] [&_svg]:shrink-0 [&_svg]:text-[var(--muted)]">
                                                        <MapPin aria-hidden="true" />
                                                        <span className="line-clamp-2">{location.address}</span>
                                                    </p>
                                                    <span className="mt-2 inline-flex items-center gap-[5px] text-[0.72rem] leading-[1.4] font-extrabold text-[var(--muted-strong)] max-[760px]:text-[0.68rem] [&_svg]:size-3.5 [&_svg]:shrink-0 [&_svg]:fill-[#1596f51f] [&_svg]:stroke-[2.2] [&_svg]:text-[var(--accent)]">
                                                        <LocateFixed aria-hidden="true" />
                                                        {coordinate(location.latitude, 'N', 'S')},{' '}
                                                        {coordinate(location.longitude, 'E', 'W')}
                                                    </span>
                                                </div>
                                            </div>

                                            <div className="col-start-2 row-start-1 flex min-w-0 flex-col items-center justify-center px-1 py-1 text-center">
                                                <p className="m-0 grid w-full flex-1 place-items-center text-[clamp(2.5rem,4.5vw,3.8rem)] leading-[0.9] font-extrabold tracking-[-0.04em] max-[430px]:text-[2.35rem]" aria-label={weather ? `${Math.round(weather.temperatureCelsius)} degrees Celsius` : 'Temperature unavailable'}>
                                                    <span className="text-[var(--ink)] drop-shadow-[0_5px_10px_rgba(39,91,155,0.16)] [-webkit-text-stroke:1px_rgba(7,21,54,0.16)]">
                                                        {weather ? `${Math.round(weather.temperatureCelsius)}°` : '—'}
                                                    </span>
                                                </p>
                                                <span className="outlined-condition max-w-full text-[0.94rem] leading-[1.25] font-semibold italic max-[430px]:text-[0.78rem]">
                                                    {weather === undefined ? 'Loading weather…' : weather === null ? 'Weather unavailable' : weather.condition}
                                                </span>
                                            </div>

                                            <div className="col-span-full row-start-2 grid grid-cols-2 items-center gap-3.5 max-[430px]:gap-2">
                                                <button type="button" className="m-0 inline-flex min-h-10 min-w-0 cursor-pointer items-center justify-center gap-2 rounded-xl border border-[var(--unstar-border)] [background:var(--unstar-background)] px-3.5 text-[0.8rem] font-extrabold text-[var(--unstar-ink)] shadow-[inset_0_1px_0_rgba(255,255,255,0.35)] transition-[color,background,transform] duration-150 hover:not-disabled:[background:var(--unstar-hover-background)] active:not-disabled:scale-[0.95] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559] disabled:cursor-wait disabled:opacity-60 motion-reduce:transition-none max-[430px]:min-h-[38px] max-[430px]:px-2 [&_svg]:size-[17px] [&_svg]:shrink-0 [&_svg]:fill-current [&_svg]:stroke-[1.8]" disabled={isRemoving} onClick={() => void handleUnstar(location)}>
                                                    {isRemoving ? <span className="size-4 animate-spin rounded-full border-2 border-white/45 border-t-current motion-reduce:animate-none" aria-hidden="true" /> : <Star aria-hidden="true" />}
                                                    <span>{isRemoving ? 'Removing…' : 'Unstar'}</span>
                                                </button>

                                                <Link className="m-0 inline-flex min-h-10 min-w-0 items-center justify-center gap-2 rounded-xl border border-white/60 bg-white/60 px-3.5 text-center text-[0.8rem] leading-tight font-bold text-[var(--control-ink)] no-underline transition-[color,background-color,transform] duration-150 hover:bg-white/75 hover:text-[#176fc2] active:scale-[0.95] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559] motion-reduce:transition-none max-[430px]:min-h-[38px] max-[430px]:px-2 [&_svg]:size-[17px] [&_svg]:shrink-0" to="/map" state={{ selectedLocation: location }} aria-label={`View ${location.name} on map`}>
                                                    <MapIcon aria-hidden="true" />
                                                    <span>View on map</span>
                                                </Link>
                                            </div>
                                        </article>
                                    </li>
                                )
                            })}
                        </ul>
                    </>
                )}
            </div>

            <PageFooterNote />
        </section>
    )
}
