import {
    Droplets,
    Map as MapIcon,
    MapPin,
    Search,
    Star,
    Thermometer,
    Wind,
    X,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCurrentWeather, type CurrentWeather } from '../api/weather'
import {
    getStarredLocations,
    searchStarredLocations,
    type StarredLocation,
    unstarLocation,
} from '../api/stars'
import { getWeatherArtwork } from '../components/weatherArtwork'
import { PageFooterNote } from '../components/PageFooterNote'

function errorMessage(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback
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
    const [searchQuery, setSearchQuery] = useState('')
    const [searchResults, setSearchResults] = useState<StarredLocation[] | null>(null)
    const [isSearching, setIsSearching] = useState(false)
    const [searchError, setSearchError] = useState('')
    const [searchRetryKey, setSearchRetryKey] = useState(0)
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
        const query = searchQuery.trim()
        if (!query) return
        if (isLoading || loadError) return

        const controller = new AbortController()

        const timeoutID = window.setTimeout(() => {
            void searchStarredLocations(query, controller.signal)
                .then((results) => {
                    if (!controller.signal.aborted) setSearchResults(results)
                })
                .catch((error: unknown) => {
                    if (!controller.signal.aborted) {
                        setSearchError(errorMessage(
                            error,
                            'Starred places could not be searched.',
                        ))
                    }
                })
                .finally(() => {
                    if (!controller.signal.aborted) setIsSearching(false)
                })
        }, 300)

        return () => {
            window.clearTimeout(timeoutID)
            controller.abort()
        }
    }, [isLoading, loadError, searchQuery, searchRetryKey])

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
                setSearchResults((current) => current?.filter(
                    (item) => item.locationID !== location.locationID,
                ) ?? null)
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

    function updateSearchQuery(value: string) {
        setSearchQuery(value)
        setSearchResults(null)
        setSearchError('')
        setIsSearching(Boolean(value.trim()))
    }

    function retrySearch() {
        setSearchResults(null)
        setSearchError('')
        setIsSearching(true)
        setSearchRetryKey((key) => key + 1)
    }

    const countLabel = isLoading || loadError
        ? 'Saved place count unavailable'
        : `${locations.length} saved ${locations.length === 1 ? 'place' : 'places'}`
    const trimmedSearchQuery = searchQuery.trim()
    const displayedLocations = trimmedSearchQuery ? searchResults ?? [] : locations

    return (
        <section className="relative z-[1] mx-auto flex h-full min-h-0 w-full max-w-[1600px] flex-col animate-fade-in max-[760px]:h-auto" aria-labelledby="starred-title">
            <header className="mb-[clamp(20px,3.5vh,34px)] flex shrink-0 items-end justify-between gap-7 max-[900px]:items-start max-[760px]:mb-4 max-[760px]:flex-col max-[760px]:gap-4">
                <div className="min-w-0 flex-1">
                    <p className="mt-0 mb-[9px] w-fit text-xs font-extrabold tracking-[0.14em] text-[var(--eyebrow)] uppercase max-[760px]:mb-1.5 max-[760px]:text-[0.68rem]">Saved locations</p>
                    <div className="flex items-center gap-[18px] max-[760px]:flex-wrap max-[760px]:gap-3">
                        <h1 className="m-0 w-fit text-[clamp(2.25rem,4vw,3.75rem)] leading-[0.98] font-extrabold tracking-[0.01em] text-[var(--ink)] [@media(max-height:720px)_and_(min-width:761px)]:text-[clamp(2.25rem,6vh,3.35rem)] max-[760px]:text-[clamp(2rem,9vw,2.65rem)]" id="starred-title">Starred places</h1>
                        <p className="mt-[5px] mb-0 shrink-0 rounded-full border border-[var(--active-border)] bg-[var(--active-surface)] px-3.5 py-[9px] text-[0.78rem] font-extrabold text-[var(--active-icon)] max-[760px]:mt-0 max-[760px]:px-3 max-[760px]:py-1.5 max-[760px]:text-[0.7rem] max-[430px]:inline-block" aria-label={countLabel}>
                            <span>{isLoading || loadError ? '—' : locations.length}</span>{' '}
                            {locations.length === 1 ? 'place' : 'places'}
                        </p>
                    </div>
                    <p className="mt-[15px] mb-0 text-base leading-[1.6] italic font-medium text-[var(--muted)] max-[760px]:mt-2.5 max-[760px]:text-[0.88rem] max-[760px]:leading-[1.45]">A quick look at the places you care about.</p>
                </div>

                <div className="relative mb-0.5 w-full max-w-[410px] shrink-0 max-[900px]:max-w-[340px] max-[760px]:max-w-none">
                    <Search className="pointer-events-none absolute top-1/2 left-4 size-[18px] -translate-y-1/2 text-[var(--muted)]" aria-hidden="true" />
                    <input
                        type="search"
                        className="starred-search-input h-12 w-full rounded-2xl border border-[var(--line)] bg-[var(--glass-surface-background)] pr-12 pl-11 text-[0.86rem] font-medium text-[var(--ink)] shadow-sm outline-none backdrop-blur-xl transition-[border-color,box-shadow,background-color] placeholder:text-[var(--muted)] focus:border-[var(--accent)] focus:bg-[var(--glass-surface-strong-background)] focus:shadow-[0_0_0_3px_var(--accent-soft)] disabled:cursor-not-allowed disabled:opacity-60"
                        value={searchQuery}
                        onChange={(event) => updateSearchQuery(event.target.value)}
                        placeholder="Search your starred places…"
                        aria-label="Search starred places"
                        disabled={isLoading || Boolean(loadError) || locations.length === 0}
                    />
                    {searchQuery && (
                        <button
                            type="button"
                            className="absolute top-1/2 right-2.5 grid size-8 -translate-y-1/2 cursor-pointer place-items-center rounded-lg border-0 bg-transparent text-[var(--muted)] transition-colors hover:bg-[var(--surface-hover)] hover:text-[var(--ink)] focus-visible:outline-3 focus-visible:outline-offset-1 focus-visible:outline-[#1596f559] [&_svg]:size-4"
                            onClick={() => updateSearchQuery('')}
                            aria-label="Clear search"
                        >
                            <X aria-hidden="true" />
                        </button>
                    )}
                </div>
            </header>

            <div className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto overscroll-contain pr-1 max-[760px]:flex-none max-[760px]:overflow-visible max-[760px]:pr-0">
                {isLoading && (
                    <div className="flex min-h-[250px] flex-col items-center justify-center gap-2.5 rounded-[26px] border border-[var(--line)] bg-[var(--surface)] px-6 py-9 text-center text-[var(--ink)] shadow-sm" role="status">
                        <span className="size-[34px] animate-spin rounded-full border-[3px] border-[#cfe8fa] border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                        <strong className="text-[1.08rem]">Loading starred places</strong>
                        <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">Finding your saved locations…</p>
                    </div>
                )}

                {!isLoading && loadError && (
                    <div className="flex min-h-[250px] flex-col items-center justify-center gap-2.5 rounded-[26px] border border-[var(--line)] bg-[var(--surface)] px-6 py-9 text-center text-[var(--ink)] shadow-sm" role="alert">
                        <strong className="text-[1.08rem] text-[#9f3340]">Starred places unavailable</strong>
                        <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">{loadError}</p>
                        <button className="mt-2 cursor-pointer rounded-[10px] border-0 bg-[var(--action-background)] hover:bg-[var(--action-hover-background)] px-3.5 py-[9px] text-[0.82rem] font-extrabold text-[var(--action-ink)] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559]" type="button" onClick={retryLoad}>Try again</button>
                    </div>
                )}

                {!isLoading && !loadError && locations.length === 0 && (
                    <div className="flex min-h-[250px] flex-col items-center justify-center gap-2.5 rounded-[26px] border border-[var(--line)] bg-[var(--surface)] px-6 py-9 text-center text-[var(--ink)] shadow-sm [&>svg]:mb-[7px] [&>svg]:size-[42px] [&>svg]:fill-[var(--accent-soft)] [&>svg]:stroke-[1.8] [&>svg]:text-[var(--accent)]">
                        <Star aria-hidden="true" />
                        <strong className="text-[1.08rem]">No starred places yet.</strong>
                        <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">Choose a place on the map and select Star to save it here.</p>
                    </div>
                )}

                {!isLoading && !loadError && locations.length > 0 && (
                    <>
                        {actionError && (
                            <p className="mt-0 mb-[13px] rounded-xl border border-[var(--unstar-border)] bg-[var(--unstar-background)] px-3.5 py-[11px] text-[0.82rem] font-bold text-[var(--unstar-ink)]" role="alert">{actionError}</p>
                        )}

                        {trimmedSearchQuery && isSearching && (
                            <div className="flex min-h-[230px] flex-col items-center justify-center gap-2.5 rounded-[26px] border border-[var(--line)] bg-[var(--surface)] px-6 py-9 text-center text-[var(--ink)] shadow-sm" role="status">
                                <span className="size-[30px] animate-spin rounded-full border-[3px] border-[#cfe8fa] border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                                <strong className="text-[1.02rem]">Searching starred places</strong>
                            </div>
                        )}

                        {trimmedSearchQuery && !isSearching && searchError && (
                            <div className="flex min-h-[230px] flex-col items-center justify-center gap-2.5 rounded-[26px] border border-[var(--line)] bg-[var(--surface)] px-6 py-9 text-center text-[var(--ink)] shadow-sm" role="alert">
                                <strong className="text-[1.08rem] text-[#9f3340]">Search unavailable</strong>
                                <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">{searchError}</p>
                                <button className="mt-1 cursor-pointer rounded-[10px] border-0 bg-[var(--action-background)] px-3.5 py-[9px] text-[0.82rem] font-extrabold text-[var(--action-ink)] hover:bg-[var(--action-hover-background)] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559]" type="button" onClick={retrySearch}>Try again</button>
                            </div>
                        )}

                        {trimmedSearchQuery && !isSearching && !searchError && displayedLocations.length === 0 && (
                            <div className="flex min-h-[230px] flex-col items-center justify-center gap-2.5 rounded-[26px] border border-[var(--line)] bg-[var(--surface)] px-6 py-9 text-center text-[var(--ink)] shadow-sm" role="status">
                                <Search className="mb-1 size-9 text-[var(--accent)]" aria-hidden="true" />
                                <strong className="text-[1.08rem]">No starred places found</strong>
                                <p className="m-0 max-w-[430px] text-[0.86rem] leading-normal text-[var(--muted)]">Try a different place name or address.</p>
                                <button className="mt-1 cursor-pointer rounded-[10px] border border-[var(--line)] bg-[var(--soft-surface)] px-3.5 py-[9px] text-[0.82rem] font-extrabold text-[var(--ink)] hover:bg-[var(--surface-hover)] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559]" type="button" onClick={() => updateSearchQuery('')}>Clear search</button>
                            </div>
                        )}

                        {!isSearching && !searchError && displayedLocations.length > 0 && (
                        <ul className="m-0 grid w-full list-none grid-cols-[repeat(auto-fit,minmax(min(100%,34rem),1fr))] gap-4 p-0 max-[430px]:gap-3" aria-label="Starred locations">
                            {displayedLocations.map((location) => {
                                const isRemoving = removingLocationIDs.has(location.locationID)
                                const weather = weatherByLocationID[location.locationID]

                                return (
                                    <li className="h-full" key={location.locationID}>
                                        <article className="hover-lift liquid-glass flex h-full min-h-[252px] flex-col rounded-[22px] border p-[18px] shadow-sm max-[430px]:min-h-[238px] max-[430px]:p-[14px]">
                                            <div className="min-w-0">
                                                <h2 className="m-0 flex items-start gap-2 text-[clamp(1.05rem,1.6vw,1.28rem)] leading-tight font-extrabold tracking-[0.008em] text-[var(--ink)] [&_svg]:mt-0.5 [&_svg]:size-[18px] [&_svg]:shrink-0 [&_svg]:text-[var(--accent)]">
                                                    <MapPin aria-hidden="true" />
                                                    <span className="line-clamp-1">{location.name}</span>
                                                </h2>
                                                <p className="mt-1.5 mb-0 pl-[26px] text-[0.78rem] leading-[1.45] text-[var(--muted)]">
                                                    <span className="line-clamp-1">{location.address}</span>
                                                </p>
                                            </div>

                                            <div className="my-4 grid flex-1 grid-cols-[minmax(0,1fr)_minmax(145px,0.9fr)] items-center gap-4 max-[430px]:grid-cols-[minmax(0,1fr)_128px] max-[430px]:gap-2">
                                                <div className="flex min-w-0 items-center gap-2 max-[430px]:gap-1">
                                                    <img className="block size-[76px] shrink-0 object-contain drop-shadow-md max-[430px]:size-[62px]" src={getWeatherArtwork(weather)} alt="" aria-hidden="true" />
                                                    <div className="min-w-0">
                                                        <p className="m-0 text-[clamp(2.5rem,4.5vw,3.45rem)] leading-[0.9] font-extrabold tracking-[-0.045em] text-[var(--ink)] max-[430px]:text-[2.35rem]" aria-label={weather ? `${Math.round(weather.temperatureCelsius)} degrees Celsius` : 'Temperature unavailable'}>
                                                            {weather ? `${Math.round(weather.temperatureCelsius)}°` : '—'}
                                                        </p>
                                                        <p className="outlined-condition mt-2 mb-0 line-clamp-2 text-[0.76rem] leading-[1.2] font-medium italic max-[430px]:text-[0.7rem]">
                                                            {weather === undefined ? 'Loading weather…' : weather === null ? 'Weather unavailable' : weather.condition}
                                                        </p>
                                                    </div>
                                                </div>

                                                <dl className="m-0 grid gap-2 text-[0.74rem] text-[var(--muted-strong)] max-[430px]:text-[0.68rem]">
                                                    <div className="flex min-w-0 items-center gap-2 [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-[var(--accent)]">
                                                        <Thermometer aria-hidden="true" />
                                                        <dt className="sr-only">Feels like</dt>
                                                        <dd className="m-0 truncate">Feels like <strong className="font-extrabold text-[var(--ink)]">{weather ? `${Math.round(weather.apparentTemperatureCelsius)}°` : '—'}</strong></dd>
                                                    </div>
                                                    <div className="flex min-w-0 items-center gap-2 [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-[var(--accent)]">
                                                        <Droplets aria-hidden="true" />
                                                        <dt className="sr-only">Humidity</dt>
                                                        <dd className="m-0 truncate">Humidity <strong className="font-extrabold text-[var(--ink)]">{weather ? `${Math.round(weather.relativeHumidityPercent)}%` : '—'}</strong></dd>
                                                    </div>
                                                    <div className="flex min-w-0 items-center gap-2 [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-[var(--accent)]">
                                                        <Wind aria-hidden="true" />
                                                        <dt className="sr-only">Wind speed</dt>
                                                        <dd className="m-0 truncate">Wind <strong className="font-extrabold text-[var(--ink)]">{weather ? `${Math.round(weather.windSpeedKilometresPerHour)} km/h` : '—'}</strong></dd>
                                                    </div>
                                                </dl>
                                            </div>

                                            <div className="grid grid-cols-2 items-center gap-3 max-[430px]:gap-2">
                                                <button type="button" className="m-0 inline-flex min-h-10 min-w-0 cursor-pointer items-center justify-center gap-2 rounded-xl border border-[var(--unstar-border)] bg-[var(--unstar-background)] px-3.5 text-[0.8rem] font-extrabold text-[var(--unstar-ink)] transition-all hover:not-disabled:bg-[var(--unstar-hover-background)] active:not-disabled:scale-[0.95] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559] disabled:cursor-wait disabled:opacity-60 motion-reduce:transition-none max-[430px]:min-h-[38px] max-[430px]:px-2 [&_svg]:size-[17px] [&_svg]:shrink-0 [&_svg]:fill-current [&_svg]:stroke-[1.8]" disabled={isRemoving} onClick={() => void handleUnstar(location)}>
                                                    {isRemoving ? <span className="size-4 animate-spin rounded-full border-2 border-white/45 border-t-current motion-reduce:animate-none" aria-hidden="true" /> : <Star aria-hidden="true" />}
                                                    <span>{isRemoving ? 'Removing…' : 'Unstar'}</span>
                                                </button>

                                                <Link className="m-0 inline-flex min-h-10 min-w-0 items-center justify-center gap-2 rounded-xl border border-[var(--line)] bg-[var(--soft-surface)] px-3.5 text-center text-[0.8rem] leading-tight font-bold text-[var(--ink)] no-underline transition-all hover:bg-[var(--surface-hover)] hover:text-[var(--accent)] active:scale-[0.95] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559] motion-reduce:transition-none max-[430px]:min-h-[38px] max-[430px]:px-2 [&_svg]:size-[17px] [&_svg]:shrink-0" to="/map" state={{ selectedLocation: location }} aria-label={`View ${location.name} on map`}>
                                                    <MapIcon aria-hidden="true" />
                                                    <span>View on map</span>
                                                </Link>
                                            </div>
                                        </article>
                                    </li>
                                )
                            })}
                        </ul>
                        )}
                    </>
                )}
            </div>

            <PageFooterNote />
        </section>
    )
}
