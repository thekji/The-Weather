import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { Minus, Navigation2, Plus, Search, X } from 'lucide-react'
import { getAuthorizationHeaders } from '../api/auth'
import { reverseGeocode, type LocationSearchResult } from '../api/locations'
import { getCurrentWeather, type CurrentWeather } from '../api/weather'

type MapConfig = {
    geoapifyMapApiKey?: string
}

type Coordinates = {
    latitude: number
    longitude: number
}

const DEFAULT_LOCATION: Coordinates = {
    latitude: 10.7769,
    longitude: 106.7009,
}

const MAP_CONTROLS_STYLES = String.raw`
    .map-search-panel {
        position: absolute;
        z-index: 500;
        top: 24px;
        left: 172px;
        width: min(760px, calc(100vw - 212px));
    }

    .map-search {
        display: flex;
        align-items: center;
        gap: 12px;
        min-height: 72px;
        padding: 9px;
        border: 1px solid rgba(255, 255, 255, 0.86);
        border-radius: 28px;
        color: var(--ink);
        background: rgba(255, 255, 255, 0.88);
        box-shadow: 0 18px 46px rgba(45, 101, 148, 0.18);
        backdrop-filter: blur(18px) saturate(125%);
        -webkit-backdrop-filter: blur(18px) saturate(125%);
    }

    .map-search-field {
        display: flex;
        align-items: center;
        min-width: 0;
        min-height: 54px;
        flex: 1 1 auto;
        gap: 14px;
        padding: 0 9px 0 20px;
        border: 1px solid #d7e5f2;
        border-radius: 21px;
        background: rgba(255, 255, 255, 0.76);
        box-shadow: inset 0 1px 2px rgba(60, 105, 143, 0.04);
    }

    .map-search-field > svg {
        flex: 0 0 auto;
        width: 25px;
        height: 25px;
        color: var(--accent);
        stroke-width: 2.2;
    }

    .map-search input {
        min-width: 0;
        flex: 1 1 auto;
        border: 0;
        outline: 0;
        color: var(--ink);
        font: inherit;
        font-size: 1rem;
        font-weight: 600;
        background: transparent;
    }

    .map-search input::placeholder {
        color: #8a96a6;
        font-weight: 400;
    }

    .map-search input::-webkit-search-cancel-button {
        display: none;
    }

    .map-search-field:focus-within {
        border-color: rgba(21, 150, 245, 0.58);
        box-shadow: 0 0 0 3px rgba(21, 150, 245, 0.1);
    }

    .map-search .map-search-submit {
        flex: 0 0 auto;
        min-height: 54px;
        padding: 0 25px;
        border: 0;
        border-radius: 20px;
        color: #ffffff;
        font: inherit;
        font-size: 0.94rem;
        font-weight: 800;
        background: var(--accent);
        box-shadow: 0 8px 20px rgba(21, 150, 245, 0.24);
        cursor: pointer;
        transition: background-color 150ms ease, transform 150ms ease;
    }

    .map-search .map-search-submit:hover:not(:disabled) {
        background: #087fd7;
        transform: translateY(-1px);
    }

    .map-search .map-search-submit:disabled {
        color: #8e9aa8;
        background: #e5ebf0;
        box-shadow: none;
        cursor: not-allowed;
    }

    .map-search .map-search-clear {
        display: grid;
        flex: 0 0 38px;
        width: 38px;
        min-height: 38px;
        padding: 0;
        border: 0;
        border-radius: 50%;
        color: #46617d;
        background: #eef6fc;
        cursor: pointer;
        place-items: center;
    }

    .map-search .map-search-clear:hover {
        color: var(--accent);
        background: #e3f2fd;
    }

    .map-search .map-search-clear svg {
        width: 20px;
        height: 20px;
        stroke-width: 2.2;
    }

    .map-search-error {
        width: fit-content;
        max-width: 100%;
        margin: 10px 0 0;
        padding: 9px 12px;
        border-radius: 12px;
        color: #9f3340;
        font-size: 0.78rem;
        font-weight: 800;
        background: rgba(255, 239, 241, 0.96);
        box-shadow: 0 10px 24px rgba(32, 43, 60, 0.12);
    }

    .map-search-results {
        max-height: min(420px, calc(100vh - 110px));
        margin: 8px 0 0;
        padding: 7px;
        overflow-y: auto;
        border: 1px solid rgba(210, 221, 229, 0.96);
        border-radius: 16px;
        list-style: none;
        background: rgba(255, 255, 255, 0.97);
        box-shadow: 0 18px 42px rgba(32, 43, 60, 0.16);
        backdrop-filter: blur(16px);
    }

    .map-search-results li + li {
        border-top: 1px solid #edf1f4;
    }

    .map-search-results button {
        display: grid;
        width: 100%;
        gap: 5px;
        padding: 14px 15px;
        border: 0;
        border-radius: 10px;
        color: var(--ink);
        font: inherit;
        text-align: left;
        background: transparent;
        cursor: pointer;
    }

    .map-search-results button:hover,
    .map-search-results button:focus-visible {
        outline: 0;
        background: #eef7fd;
    }

    .map-search-results strong {
        font-size: 1rem;
        font-weight: 500;
    }

    .map-search-results span {
        color: var(--muted);
        font-size: 0.88rem;
        font-weight: 400;
        line-height: 1.35;
    }

    .map-search-results small {
        color: #82909f;
        font-size: 0.78rem;
        font-weight: 400;
    }

    .map-location-control {
        position: absolute;
        z-index: 500;
        bottom: 30px;
        left: 136px;
        display: grid;
        gap: 8px;
    }

    .map-location-control p {
        max-width: 260px;
        margin: 0;
        padding: 9px 12px;
        border-radius: 12px;
        color: #9f3340;
        font-size: 0.76rem;
        font-weight: 700;
        background: rgba(255, 239, 241, 0.96);
        box-shadow: 0 8px 22px rgba(32, 43, 60, 0.12);
    }

    .map-location-control button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 10px;
        min-height: 52px;
        padding: 0 22px;
        border: 1px solid rgba(255, 255, 255, 0.88);
        border-radius: 18px;
        color: var(--ink);
        font: inherit;
        font-size: 0.9rem;
        font-weight: 800;
        background: rgba(255, 255, 255, 0.94);
        box-shadow: 0 14px 34px rgba(32, 43, 60, 0.15);
        backdrop-filter: blur(16px);
        -webkit-backdrop-filter: blur(16px);
        cursor: pointer;
        transition: color 150ms ease, transform 150ms ease;
    }

    .map-location-control button:hover:not(:disabled) {
        color: var(--accent);
        transform: translateY(-1px);
    }

    .map-location-control button:disabled {
        color: #718196;
        cursor: wait;
    }

    .map-location-control svg,
    .map-location-spinner {
        width: 21px;
        height: 21px;
        color: var(--accent);
    }

    .map-location-spinner {
        border: 2px solid #cfe8fa;
        border-top-color: var(--accent);
        border-radius: 50%;
        animation: map-location-spin 800ms linear infinite;
    }

    .map-zoom-control {
        position: absolute;
        z-index: 500;
        right: 24px;
        bottom: 30px;
        overflow: hidden;
        border: 1px solid rgba(210, 221, 229, 0.92);
        border-radius: 17px;
        background: rgba(255, 255, 255, 0.95);
        box-shadow: 0 14px 34px rgba(32, 43, 60, 0.15);
        backdrop-filter: blur(16px);
        -webkit-backdrop-filter: blur(16px);
    }

    .map-zoom-control button {
        display: grid;
        width: 52px;
        height: 52px;
        padding: 0;
        border: 0;
        color: var(--ink);
        background: transparent;
        cursor: pointer;
        place-items: center;
        transition: color 150ms ease, background-color 150ms ease;
    }

    .map-zoom-control button + button {
        border-top: 1px solid #dde7ef;
    }

    .map-zoom-control button:hover {
        color: var(--accent);
        background: #eef7fd;
    }

    .map-zoom-control svg {
        width: 25px;
        height: 25px;
        stroke-width: 2.2;
    }

    @keyframes map-location-spin {
        to { transform: rotate(360deg); }
    }

    @media (max-width: 1100px) and (min-width: 761px) {
        .map-search-panel {
            top: 20px;
            left: 136px;
            width: min(680px, calc(100vw - 160px));
        }

        .map-search {
            min-height: 64px;
            gap: 9px;
            padding: 8px;
            border-radius: 24px;
        }

        .map-search-field {
            min-height: 48px;
            gap: 11px;
            padding-left: 16px;
            border-radius: 18px;
        }

        .map-search-field > svg {
            width: 22px;
            height: 22px;
        }

        .map-search input {
            font-size: 0.9rem;
        }

        .map-search .map-search-submit {
            min-height: 48px;
            padding: 0 20px;
            border-radius: 17px;
            font-size: 0.86rem;
        }

        .map-location-control {
            left: 136px;
        }

        .map-zoom-control {
            right: 20px;
        }

        .weather-modal {
            top: 108px;
            right: 20px;
            width: min(330px, calc(100vw - 156px));
            max-height: calc(100svh - 138px);
            overflow-y: auto;
        }
    }

    @media (max-width: 760px) {
        .map-search-panel {
            top: 16px;
            left: 16px;
            width: calc(100vw - 32px);
        }

        .map-search {
            min-height: 58px;
            gap: 7px;
            padding: 7px;
            border-radius: 22px;
        }

        .map-search-field {
            min-height: 44px;
            gap: 9px;
            padding-right: 6px;
            padding-left: 13px;
            border-radius: 16px;
        }

        .map-search-field > svg {
            width: 21px;
            height: 21px;
        }

        .map-search input {
            font-size: 0.86rem;
        }

        .map-search .map-search-submit {
            min-height: 44px;
            padding: 0 14px;
            border-radius: 15px;
            font-size: 0.8rem;
        }

        .map-search .map-search-clear {
            flex-basis: 32px;
            width: 32px;
            min-height: 32px;
        }

        .map-location-control {
            bottom: 94px;
            left: 16px;
        }

        .map-location-control button {
            min-height: 46px;
            padding: 0 16px;
            border-radius: 16px;
            font-size: 0.8rem;
        }

        .map-zoom-control {
            right: 16px;
            bottom: 94px;
            border-radius: 15px;
        }

        .map-zoom-control button {
            width: 46px;
            height: 46px;
        }
    }

    @media (max-width: 520px) {
        .map-search-panel {
            top: 12px;
            left: 12px;
            width: calc(100vw - 24px);
        }

        .map-search {
            gap: 6px;
            padding: 6px;
            border-radius: 20px;
        }

        .map-search-field {
            gap: 7px;
            padding-left: 11px;
        }

        .map-search .map-search-submit {
            min-width: 60px;
            padding: 0 10px;
        }

        .map-location-control {
            bottom: 90px;
            left: 12px;
        }

        .map-location-control button {
            padding: 0 14px;
        }

        .map-zoom-control {
            right: 12px;
            bottom: 90px;
        }
    }

    @media (max-height: 700px) and (min-width: 761px) {
        .weather-modal {
            top: 96px;
            max-height: calc(100svh - 116px);
            overflow-y: auto;
        }

        .map-location-control,
        .map-zoom-control {
            bottom: 20px;
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .map-location-spinner {
            animation: none;
        }
    }
`

export function MapPage() {
    const mapElementRef = useRef<HTMLDivElement>(null)
    const mapRef = useRef<L.Map | null>(null)
    const markerRef = useRef<L.Marker | null>(null)
    const weatherRequestRef = useRef<AbortController | null>(null)
    const reverseGeocodeRequestRef = useRef<AbortController | null>(null)
    const [mapReady, setMapReady] = useState(false)
    const [mapError, setMapError] = useState('')
    const [searchQuery, setSearchQuery] = useState('')
    const [searchError, setSearchError] = useState('')
    const [isSearching, setIsSearching] = useState(false)
    const [searchResults, setSearchResults] = useState<LocationSearchResult[]>([])
    const [selectedLocation, setSelectedLocation] = useState<LocationSearchResult | null>(null)
    const [currentWeather, setCurrentWeather] = useState<CurrentWeather | null>(null)
    const [weatherError, setWeatherError] = useState('')
    const [isWeatherLoading, setIsWeatherLoading] = useState(false)
    const [isLocating, setIsLocating] = useState(false)
    const [locationError, setLocationError] = useState('')

    const selectLocation = useCallback(async (result: LocationSearchResult) => {
        const latLng: L.LatLngExpression = [result.latitude, result.longitude]
        mapRef.current?.flyTo(latLng, 18, { duration: 0.8 })
        markerRef.current?.setLatLng(latLng)
        setSearchQuery(result.address)
        setSearchResults([])
        setSearchError('')
        setSelectedLocation(result)
        setCurrentWeather(null)
        setWeatherError('')
        setIsWeatherLoading(true)

        weatherRequestRef.current?.abort()
        const controller = new AbortController()
        weatherRequestRef.current = controller

        try {
            const weather = await getCurrentWeather({
                latitude: result.latitude,
                longitude: result.longitude,
            }, controller.signal)
            setCurrentWeather(weather)
        } catch (error) {
            if (!controller.signal.aborted) {
                setWeatherError(
                    error instanceof Error ? error.message : 'Current weather could not be loaded.',
                )
            }
        } finally {
            if (!controller.signal.aborted) {
                setIsWeatherLoading(false)
            }
        }
    }, [])

    useEffect(() => {
        const mapElement = mapElementRef.current
        if (!mapElement) return

        let cancelled = false

        async function initialiseMap() {
            try {
                const response = await fetch('/api/config', {
                    headers: {
                        Accept: 'application/json',
                        ...getAuthorizationHeaders(),
                    },
                })

                if (!response.ok) {
                    throw new Error(`Map configuration request failed (${response.status})`)
                }

                const config = (await response.json()) as MapConfig
                const apiKey = config.geoapifyMapApiKey?.trim()

                if (!apiKey) {
                    throw new Error('Add GEOAPIFY_KEY to the backend environment to display the map.')
                }

                if (cancelled || !mapElementRef.current) return
                const map = L.map(mapElementRef.current, {
                    center: [DEFAULT_LOCATION.latitude, DEFAULT_LOCATION.longitude],
                    zoom: 12,
                    zoomControl: false,
                })

                L.tileLayer(
                    `https://maps.geoapify.com/v1/tile/osm-bright/{z}/{x}/{y}.png?apiKey=${encodeURIComponent(apiKey)}`,
                    {
                        attribution:
                            '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://www.geoapify.com/">Geoapify</a>',
                        maxZoom: 20,
                    },
                ).addTo(map)

                const markerIcon = L.divIcon({
                    className: 'weather-map-marker-wrap',
                    html: '<span class="weather-map-marker"><span></span></span>',
                    iconAnchor: [18, 42],
                    iconSize: [36, 44],
                })

                markerRef.current = L.marker(
                    [DEFAULT_LOCATION.latitude, DEFAULT_LOCATION.longitude],
                    { icon: markerIcon, title: 'Selected location' },
                ).addTo(map)

                map.on('click', ({ latlng }) => {
                    markerRef.current?.setLatLng(latlng)

                    reverseGeocodeRequestRef.current?.abort()
                    weatherRequestRef.current?.abort()
                    const controller = new AbortController()
                    reverseGeocodeRequestRef.current = controller
                    setSearchResults([])
                    setSearchError('')
                    setSelectedLocation(null)
                    setCurrentWeather(null)

                    void reverseGeocode(latlng.lat, latlng.lng, controller.signal)
                            .then((location) => selectLocation(location))
                            .catch((error: unknown) => {
                                if (!controller.signal.aborted) {
                                    setSearchError(
                                        error instanceof Error
                                            ? error.message
                                            : 'Could not identify this map location.',
                                    )
                                }
                            })
                })

                mapRef.current = map
                setMapReady(true)
            } catch (error) {
                if (!cancelled) {
                    setMapError(error instanceof Error ? error.message : 'The map could not be loaded.')
                }
            }
        }

        void initialiseMap()

        return () => {
            cancelled = true
            reverseGeocodeRequestRef.current?.abort()
            weatherRequestRef.current?.abort()
            mapRef.current?.remove()
            mapRef.current = null
            markerRef.current = null
        }
    }, [selectLocation])

    function moveMarker(nextCoordinates: Coordinates, zoom = 13) {
        const latLng: L.LatLngExpression = [nextCoordinates.latitude, nextCoordinates.longitude]

        mapRef.current?.flyTo(latLng, zoom, { duration: 0.8 })
        markerRef.current?.setLatLng(latLng)
    }

    async function searchLocation(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        const query = searchQuery.trim()
        if (!query) return

        setIsSearching(true)
        setSearchError('')
        setSearchResults([])

        try {
            const searchParams = new URLSearchParams({ q: query })
            const response = await fetch(`/api/locations/search?${searchParams}`, {
                headers: {
                    Accept: 'application/json',
                    ...getAuthorizationHeaders(),
                },
            })

            if (!response.ok) {
                throw new Error('Search failed. Please try another place.')
            }

            const results = (await response.json()) as LocationSearchResult[]

            if (!Array.isArray(results) || results.length === 0) {
                throw new Error('No matching place found.')
            }

            setSearchResults(results)
        } catch (error) {
            setSearchError(error instanceof Error ? error.message : 'Search failed. Please try another place.')
        } finally {
            setIsSearching(false)
        }
    }

    async function selectSearchResult(result: LocationSearchResult) {
        reverseGeocodeRequestRef.current?.abort()
        await selectLocation(result)
    }

    function closeWeatherModal() {
        reverseGeocodeRequestRef.current?.abort()
        weatherRequestRef.current?.abort()
        weatherRequestRef.current = null
        setSelectedLocation(null)
        setCurrentWeather(null)
        setWeatherError('')
        setIsWeatherLoading(false)
    }

    function clearSearch() {
        setSearchError('')
        setSearchResults([])
        setSearchQuery('')
    }

    function goToCurrentLocation() {
        setLocationError('')

        if (!navigator.geolocation) {
            setLocationError('Location is not supported by this browser.')
            return
        }

        setIsLocating(true)
        navigator.geolocation.getCurrentPosition(
            (position) => {
                const coordinates = {
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                }

                moveMarker(coordinates, 16)
                reverseGeocodeRequestRef.current?.abort()
                const controller = new AbortController()
                reverseGeocodeRequestRef.current = controller

                void (async () => {
                    let location: LocationSearchResult

                    try {
                        location = await reverseGeocode(
                            coordinates.latitude,
                            coordinates.longitude,
                            controller.signal,
                        )
                    } catch {
                        if (controller.signal.aborted) return

                        location = {
                            locationID: `current-${coordinates.latitude}-${coordinates.longitude}`,
                            name: 'My location',
                            address: 'Current location',
                            ...coordinates,
                        }
                    }

                    await selectLocation(location)
                })().finally(() => setIsLocating(false))
            },
            (error) => {
                const message = error.code === error.PERMISSION_DENIED
                    ? 'Allow location access to find your position.'
                    : 'Your current location could not be found.'

                setLocationError(message)
                setIsLocating(false)
            },
            {
                enableHighAccuracy: true,
                maximumAge: 60_000,
                timeout: 12_000,
            },
        )
    }

    return (
        <section className="map-page" aria-label="Map">
            <style>{MAP_CONTROLS_STYLES}</style>
            <div className="map-frame">
                <div
                    ref={mapElementRef}
                    className="weather-map"
                    aria-label="Interactive weather location map"
                />

                {!mapReady && !mapError && (
                    <div className="map-message" role="status">
                        <span className="map-loader" aria-hidden="true" />
                        <strong>Preparing your map</strong>
                        <small>Loading the map and location tools…</small>
                    </div>
                )}

                {mapError && (
                    <div className="map-message map-message-error" role="alert">
                        <span className="map-error-icon" aria-hidden="true">!</span>
                        <strong>Map unavailable</strong>
                        <small>{mapError}</small>
                    </div>
                )}

                {mapReady && !mapError && (
                    <div className="map-search-panel">
                        <form className="map-search" role="search" onSubmit={searchLocation}>
                            <div className="map-search-field">
                                <Search aria-hidden="true" />
                                <input
                                    id="map-location-search"
                                    name="location"
                                    type="search"
                                    value={searchQuery}
                                    placeholder="Search place or address"
                                    aria-label="Search place or address"
                                    onChange={(event) => {
                                        setSearchQuery(event.target.value)
                                        setSearchResults([])
                                    }}
                                />
                                {searchQuery && (
                                    <button
                                        className="map-search-clear"
                                        type="button"
                                        aria-label="Clear search"
                                        onClick={clearSearch}
                                    >
                                        <X aria-hidden="true" />
                                    </button>
                                )}
                            </div>
                            <button
                                className="map-search-submit"
                                type="submit"
                                disabled={isSearching || !searchQuery.trim()}
                            >
                                {isSearching ? 'Searching' : 'Search'}
                            </button>
                        </form>

                        {searchError && <p className="map-search-error">{searchError}</p>}

                        {searchResults.length > 0 && (
                            <ul className="map-search-results" aria-label="Location search results">
                                {searchResults.map((result) => (
                                    <li key={`${result.address}-${result.latitude}-${result.longitude}`}>
                                        <button type="button" onClick={() => selectSearchResult(result)}>
                                            <strong>{result.name}</strong>
                                            <span>{result.address}</span>
                                            <small>
                                                {result.latitude.toFixed(5)}, {result.longitude.toFixed(5)}
                                            </small>
                                        </button>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                )}

                {mapReady && !mapError && (
                    <>
                        <div className="map-location-control">
                            {locationError && <p role="alert">{locationError}</p>}
                            <button type="button" onClick={goToCurrentLocation} disabled={isLocating}>
                                {isLocating
                                    ? <span className="map-location-spinner" aria-hidden="true" />
                                    : <Navigation2 aria-hidden="true" />}
                                <span>{isLocating ? 'Locating' : 'My location'}</span>
                            </button>
                        </div>

                        <div className="map-zoom-control" aria-label="Map zoom controls">
                            <button type="button" aria-label="Zoom in" onClick={() => mapRef.current?.zoomIn()}>
                                <Plus aria-hidden="true" />
                            </button>
                            <button type="button" aria-label="Zoom out" onClick={() => mapRef.current?.zoomOut()}>
                                <Minus aria-hidden="true" />
                            </button>
                        </div>
                    </>
                )}

                {selectedLocation && (
                    <aside
                        className="weather-modal"
                        role="dialog"
                        aria-modal="false"
                        aria-labelledby="weather-modal-title"
                    >
                        <button
                            className="weather-modal-close"
                            type="button"
                            aria-label="Close current weather"
                            onClick={closeWeatherModal}
                        >
                            <X aria-hidden="true" />
                        </button>

                        <p className="weather-modal-eyebrow">Current weather</p>
                        <h2 id="weather-modal-title">{selectedLocation.name}</h2>
                        <p className="weather-modal-address">{selectedLocation.address}</p>

                        {isWeatherLoading && (
                            <div className="weather-modal-status" role="status">
                                <span className="map-loader" aria-hidden="true" />
                                <span>Loading current weather…</span>
                            </div>
                        )}

                        {weatherError && (
                            <div className="weather-modal-status weather-modal-status--error" role="alert">
                                <span>{weatherError}</span>
                                <button type="button" onClick={() => void selectSearchResult(selectedLocation)}>
                                    Try again
                                </button>
                            </div>
                        )}

                        {currentWeather && (
                            <div className="weather-modal-content">
                                <div className="weather-modal-primary">
                                    <span className={`weather-orb${currentWeather.daytime ? '' : ' weather-orb--night'}`} aria-hidden="true" />
                                    <div>
                                        <p className="weather-modal-temperature">
                                            {Math.round(currentWeather.temperatureCelsius)}<span>°</span>
                                        </p>
                                        <p className="weather-modal-condition">{currentWeather.condition}</p>
                                    </div>
                                </div>

                                <dl className="weather-modal-details">
                                    <div>
                                        <dt>Feels like</dt>
                                        <dd>{Math.round(currentWeather.apparentTemperatureCelsius)}°C</dd>
                                    </div>
                                    <div>
                                        <dt>Humidity</dt>
                                        <dd>{Math.round(currentWeather.relativeHumidityPercent)}%</dd>
                                    </div>
                                    <div>
                                        <dt>Wind</dt>
                                        <dd>{Math.round(currentWeather.windSpeedKilometresPerHour)} km/h</dd>
                                    </div>
                                    <div>
                                        <dt>Rain</dt>
                                        <dd>{currentWeather.rainMillimetres.toFixed(1)} mm</dd>
                                    </div>
                                </dl>

                                <p className="weather-modal-updated">
                                    Updated {currentWeather.recordedAt.replace('T', ' ')}
                                </p>
                            </div>
                        )}
                    </aside>
                )}
            </div>
        </section>
    )
}
