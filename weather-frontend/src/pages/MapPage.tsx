import { useEffect, useRef, useState, type FormEvent } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

type MapConfig = {
    geoapifyMapApiKey?: string
}

type Coordinates = {
    latitude: number
    longitude: number
}

type LocationSearchResult = {
    name: string
    address: string
    longitude: number
    latitude: number
}

type CurrentWeather = {
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

const DEFAULT_LOCATION: Coordinates = {
    latitude: 10.7769,
    longitude: 106.7009,
}

function SearchIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <circle cx="11" cy="11" r="7" />
            <path d="m16.5 16.5 4 4" />
        </svg>
    )
}

function CrosshairIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <circle cx="12" cy="12" r="7" />
            <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
        </svg>
    )
}

function CloseIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path d="M6 6l12 12M18 6 6 18" />
        </svg>
    )
}

export function MapPage() {
    const mapElementRef = useRef<HTMLDivElement>(null)
    const mapRef = useRef<L.Map | null>(null)
    const markerRef = useRef<L.Marker | null>(null)
    const weatherRequestRef = useRef<AbortController | null>(null)
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

    useEffect(() => {
        const mapElement = mapElementRef.current
        if (!mapElement) return

        let cancelled = false

        async function initialiseMap() {
            try {
                const response = await fetch('/api/config', {
                    headers: { Accept: 'application/json' },
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

                L.control.zoom({ position: 'bottomright' }).addTo(map)
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
            weatherRequestRef.current?.abort()
            mapRef.current?.remove()
            mapRef.current = null
            markerRef.current = null
        }
    }, [])

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
                headers: { Accept: 'application/json' },
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
        moveMarker({
            latitude: result.latitude,
            longitude: result.longitude,
        }, 16)
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
            const searchParams = new URLSearchParams({
                latitude: String(result.latitude),
                longitude: String(result.longitude),
            })
            const response = await fetch(`/api/weather?${searchParams}`, {
                headers: { Accept: 'application/json' },
                signal: controller.signal,
            })

            if (!response.ok) {
                throw new Error('Current weather could not be loaded.')
            }

            const weather = (await response.json()) as CurrentWeather
            if (typeof weather.temperatureCelsius !== 'number' || !weather.condition) {
                throw new Error('The weather service returned an invalid response.')
            }
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
    }

    function closeWeatherModal() {
        weatherRequestRef.current?.abort()
        weatherRequestRef.current = null
        setSelectedLocation(null)
        setCurrentWeather(null)
        setWeatherError('')
        setIsWeatherLoading(false)
    }

    function recenterMap() {
        setSearchError('')
        setSearchResults([])
        closeWeatherModal()
        moveMarker(DEFAULT_LOCATION, 12)
    }

    return (
        <section className="map-page" aria-label="Map">
            <div className="map-frame">
                <div
                    ref={mapElementRef}
                    className="weather-map"
                    aria-label="Interactive weather location map"
                />

                {!mapReady && !mapError && (
                    <div className="map-message" role="status">
                        <span className="map-loader" aria-hidden="true" />
                        <strong>Loading map</strong>
                        <small>Connecting to Geoapify…</small>
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
                            <SearchIcon />
                            <input
                                type="search"
                                value={searchQuery}
                                placeholder="Search place or address"
                                aria-label="Search place or address"
                                onChange={(event) => {
                                    setSearchQuery(event.target.value)
                                    setSearchResults([])
                                }}
                            />
                            <button type="submit" disabled={isSearching || !searchQuery.trim()}>
                                {isSearching ? 'Searching' : 'Search'}
                            </button>
                            <button
                                className="map-recenter"
                                type="button"
                                aria-label="Recenter to Ho Chi Minh City"
                                onClick={recenterMap}
                            >
                                <CrosshairIcon />
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
                            <CloseIcon />
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
