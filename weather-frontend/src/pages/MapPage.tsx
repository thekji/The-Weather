import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
    CalendarDays,
    Cloud,
    CloudRain,
    Clock,
    Compass,
    Droplets,
    MapPin,
    Minus,
    Moon,
    Navigation2,
    Plus,
    Search,
    Sun,
    Umbrella,
    Wind,
    X,
} from 'lucide-react'
import { useLocation } from 'react-router-dom'
import { getAuthorizationHeaders } from '../api/auth'
import {
    isLocationSearchResult,
    reverseGeocode,
    type LocationSearchResult,
} from '../api/locations'
import { getStarredLocations, starLocation, unstarLocation } from '../api/stars'
import {
    getCurrentWeather,
    getDailyWeather,
    getHourlyWeather,
    type CurrentWeather,
    type DailyWeatherForecast,
    type HourlyWeatherForecast,
} from '../api/weather'
import { DailyForecast } from '../components/DailyForecast'
import { HourlyForecast } from '../components/HourlyForecast'
import { StarButton } from '../components/StarButton'
import { getWeatherArtwork } from '../components/weatherArtwork'

type MapConfig = {
    geoapifyMapApiKey?: string
}

type Coordinates = {
    latitude: number
    longitude: number
}

type MapRouteState = {
    selectedLocation?: unknown
}

const DEFAULT_LOCATION: Coordinates = {
    latitude: 10.7769,
    longitude: 106.7009,
}

function compassDirection(degrees: number): string {
    const directions = [
        'N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE',
        'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW',
    ]
    const normalized = ((degrees % 360) + 360) % 360
    return directions[Math.round(normalized / 22.5) % directions.length]
}

function formattedCoordinate(value: number, positive: string, negative: string): string {
    return `${Math.abs(value).toFixed(4)}° ${value >= 0 ? positive : negative}`
}

export function MapPage() {
    const route = useLocation()
    const mapElementRef = useRef<HTMLDivElement>(null)
    const mapRef = useRef<L.Map | null>(null)
    const markerRef = useRef<L.Marker | null>(null)
    const weatherRequestRef = useRef<AbortController | null>(null)
    const hourlyWeatherRequestRef = useRef<AbortController | null>(null)
    const dailyWeatherRequestRef = useRef<AbortController | null>(null)
    const reverseGeocodeRequestRef = useRef<AbortController | null>(null)
    const starActionRequestsRef = useRef(new Map<string, AbortController>())
    const handledRouteKeyRef = useRef<string | null>(null)
    const [mapReady, setMapReady] = useState(false)
    const [mapError, setMapError] = useState('')
    const [searchQuery, setSearchQuery] = useState('')
    const [searchError, setSearchError] = useState('')
    const [isSearching, setIsSearching] = useState(false)
    const [searchResults, setSearchResults] = useState<LocationSearchResult[]>([])
    const [selectedLocation, setSelectedLocation] = useState<LocationSearchResult | null>(null)
    const [selectedLocationIsNormalized, setSelectedLocationIsNormalized] = useState(true)
    const [currentWeather, setCurrentWeather] = useState<CurrentWeather | null>(null)
    const [weatherError, setWeatherError] = useState('')
    const [isWeatherLoading, setIsWeatherLoading] = useState(false)
    const [hourlyWeather, setHourlyWeather] = useState<HourlyWeatherForecast | null>(null)
    const [hourlyWeatherError, setHourlyWeatherError] = useState('')
    const [isHourlyWeatherLoading, setIsHourlyWeatherLoading] = useState(false)
    const [dailyWeather, setDailyWeather] = useState<DailyWeatherForecast | null>(null)
    const [dailyWeatherError, setDailyWeatherError] = useState('')
    const [isDailyWeatherLoading, setIsDailyWeatherLoading] = useState(false)
    const [isLocating, setIsLocating] = useState(false)
    const [locationError, setLocationError] = useState('')
    const [starredLocationIDs, setStarredLocationIDs] = useState<Set<string>>(
        () => new Set(),
    )
    const [isStarsLoading, setIsStarsLoading] = useState(true)
    const [starsLoadError, setStarsLoadError] = useState('')
    const [starActionError, setStarActionError] = useState<{
        locationID: string
        message: string
    } | null>(null)
    const [updatingStarLocationIDs, setUpdatingStarLocationIDs] = useState<Set<string>>(
        () => new Set(),
    )
    const [starsReloadKey, setStarsReloadKey] = useState(0)
    const routeState = route.state as MapRouteState | null
    const routedLocation = isLocationSearchResult(routeState?.selectedLocation)
        ? routeState.selectedLocation
        : null

    const selectLocation = useCallback(async (
        result: LocationSearchResult,
        isNormalized = true,
    ) => {
        const latLng: L.LatLngExpression = [result.latitude, result.longitude]
        mapRef.current?.flyTo(latLng, 18, { duration: 0.8 })
        markerRef.current?.setLatLng(latLng)
        setSearchQuery(result.address)
        setSearchResults([])
        setSearchError('')
        setSelectedLocation(result)
        setSelectedLocationIsNormalized(isNormalized)
        setCurrentWeather(null)
        setWeatherError('')
        setIsWeatherLoading(true)
        setHourlyWeather(null)
        setHourlyWeatherError('')
        setIsHourlyWeatherLoading(true)
        setDailyWeather(null)
        setDailyWeatherError('')
        setIsDailyWeatherLoading(true)

        weatherRequestRef.current?.abort()
        hourlyWeatherRequestRef.current?.abort()
        dailyWeatherRequestRef.current?.abort()
        const weatherController = new AbortController()
        const hourlyController = new AbortController()
        const dailyController = new AbortController()
        weatherRequestRef.current = weatherController
        hourlyWeatherRequestRef.current = hourlyController
        dailyWeatherRequestRef.current = dailyController
        const coordinates = {
            latitude: result.latitude,
            longitude: result.longitude,
        }

        const currentWeatherRequest = getCurrentWeather(
            coordinates,
            weatherController.signal,
        ).then((weather) => {
            if (!weatherController.signal.aborted) setCurrentWeather(weather)
        }).catch((error: unknown) => {
            if (!weatherController.signal.aborted) {
                setWeatherError(
                    error instanceof Error ? error.message : 'Current weather could not be loaded.',
                )
            }
        }).finally(() => {
            if (!weatherController.signal.aborted) setIsWeatherLoading(false)
        })

        const hourlyWeatherRequest = getHourlyWeather(
            coordinates,
            hourlyController.signal,
        ).then((forecast) => {
            if (!hourlyController.signal.aborted) setHourlyWeather(forecast)
        }).catch((error: unknown) => {
            if (!hourlyController.signal.aborted) {
                setHourlyWeatherError(
                    error instanceof Error ? error.message : 'Hourly forecast could not be loaded.',
                )
            }
        }).finally(() => {
            if (!hourlyController.signal.aborted) setIsHourlyWeatherLoading(false)
        })

        const dailyWeatherRequest = getDailyWeather(
            coordinates,
            dailyController.signal,
        ).then((forecast) => {
            if (!dailyController.signal.aborted) setDailyWeather(forecast)
        }).catch((error: unknown) => {
            if (!dailyController.signal.aborted) {
                setDailyWeatherError(
                    error instanceof Error ? error.message : 'Daily forecast could not be loaded.',
                )
            }
        }).finally(() => {
            if (!dailyController.signal.aborted) setIsDailyWeatherLoading(false)
        })

        await Promise.all([currentWeatherRequest, hourlyWeatherRequest, dailyWeatherRequest])
    }, [])

    useEffect(() => {
        const controller = new AbortController()

        void getStarredLocations(controller.signal)
            .then((locations) => {
                if (!controller.signal.aborted) {
                    setStarredLocationIDs(new Set(
                        locations.map((location) => location.locationID),
                    ))
                }
            })
            .catch((error: unknown) => {
                if (!controller.signal.aborted) {
                    setStarsLoadError(
                        error instanceof Error
                            ? error.message
                            : 'Starred places could not be loaded.',
                    )
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setIsStarsLoading(false)
            })

        return () => controller.abort()
    }, [starsReloadKey])

    useEffect(() => {
        const requests = starActionRequestsRef.current

        return () => {
            requests.forEach((controller) => controller.abort())
            requests.clear()
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
                    hourlyWeatherRequestRef.current?.abort()
                    dailyWeatherRequestRef.current?.abort()
                    const controller = new AbortController()
                    reverseGeocodeRequestRef.current = controller
                    setSearchResults([])
                    setSearchError('')
                    setSelectedLocation(null)
                    setCurrentWeather(null)
                    setHourlyWeather(null)
                    setDailyWeather(null)

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
            hourlyWeatherRequestRef.current?.abort()
            dailyWeatherRequestRef.current?.abort()
            mapRef.current?.remove()
            mapRef.current = null
            markerRef.current = null
        }
    }, [selectLocation])

    useEffect(() => {
        if (!mapReady || !routedLocation || handledRouteKeyRef.current === route.key) {
            return
        }

        handledRouteKeyRef.current = route.key
        void selectLocation(routedLocation)
    }, [mapReady, route.key, routedLocation, selectLocation])

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

            const results: unknown = await response.json()

            if (!Array.isArray(results) || !results.every(isLocationSearchResult)) {
                throw new Error('The location service returned an invalid response.')
            }

            if (results.length === 0) {
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
        hourlyWeatherRequestRef.current?.abort()
        dailyWeatherRequestRef.current?.abort()
        weatherRequestRef.current = null
        hourlyWeatherRequestRef.current = null
        dailyWeatherRequestRef.current = null
        setSelectedLocation(null)
        setSelectedLocationIsNormalized(true)
        setCurrentWeather(null)
        setWeatherError('')
        setIsWeatherLoading(false)
        setHourlyWeather(null)
        setHourlyWeatherError('')
        setIsHourlyWeatherLoading(false)
        setDailyWeather(null)
        setDailyWeatherError('')
        setIsDailyWeatherLoading(false)
    }

    async function retryHourlyForecast() {
        if (!selectedLocation) return

        hourlyWeatherRequestRef.current?.abort()
        const controller = new AbortController()
        hourlyWeatherRequestRef.current = controller
        setHourlyWeather(null)
        setHourlyWeatherError('')
        setIsHourlyWeatherLoading(true)

        try {
            const forecast = await getHourlyWeather({
                latitude: selectedLocation.latitude,
                longitude: selectedLocation.longitude,
            }, controller.signal)
            if (!controller.signal.aborted) setHourlyWeather(forecast)
        } catch (error) {
            if (!controller.signal.aborted) {
                setHourlyWeatherError(
                    error instanceof Error ? error.message : 'Hourly forecast could not be loaded.',
                )
            }
        } finally {
            if (!controller.signal.aborted) setIsHourlyWeatherLoading(false)
        }
    }

    async function retryDailyForecast() {
        if (!selectedLocation) return

        dailyWeatherRequestRef.current?.abort()
        const controller = new AbortController()
        dailyWeatherRequestRef.current = controller
        setDailyWeather(null)
        setDailyWeatherError('')
        setIsDailyWeatherLoading(true)

        try {
            const forecast = await getDailyWeather({
                latitude: selectedLocation.latitude,
                longitude: selectedLocation.longitude,
            }, controller.signal)
            if (!controller.signal.aborted) setDailyWeather(forecast)
        } catch (error) {
            if (!controller.signal.aborted) {
                setDailyWeatherError(
                    error instanceof Error ? error.message : 'Daily forecast could not be loaded.',
                )
            }
        } finally {
            if (!controller.signal.aborted) setIsDailyWeatherLoading(false)
        }
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
                    let locationIsNormalized = true

                    try {
                        location = await reverseGeocode(
                            coordinates.latitude,
                            coordinates.longitude,
                            controller.signal,
                        )
                    } catch {
                        if (controller.signal.aborted) return

                        locationIsNormalized = false
                        location = {
                            locationID: `current-${coordinates.latitude}-${coordinates.longitude}`,
                            name: 'My location',
                            address: 'Current location',
                            ...coordinates,
                        }
                    }

                    await selectLocation(location, locationIsNormalized)
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

    async function toggleSelectedLocationStar() {
        const location = selectedLocation
        if (!location
            || !selectedLocationIsNormalized
            || isStarsLoading
            || starsLoadError
            || starActionRequestsRef.current.has(location.locationID)) {
            return
        }

        const wasStarred = starredLocationIDs.has(location.locationID)
        const controller = new AbortController()
        starActionRequestsRef.current.set(location.locationID, controller)
        setStarActionError(null)
        setUpdatingStarLocationIDs((current) => {
            const next = new Set(current)
            next.add(location.locationID)
            return next
        })

        try {
            if (wasStarred) {
                await unstarLocation(location.locationID, controller.signal)
            } else {
                const starredLocation = await starLocation(location, controller.signal)
                if (starredLocation.locationID !== location.locationID) {
                    throw new Error('The star service returned a different location.')
                }
            }

            if (!controller.signal.aborted) {
                setStarredLocationIDs((current) => {
                    const next = new Set(current)
                    if (wasStarred) next.delete(location.locationID)
                    else next.add(location.locationID)
                    return next
                })
            }
        } catch (error) {
            if (!controller.signal.aborted) {
                setStarActionError({
                    locationID: location.locationID,
                    message: error instanceof Error
                        ? error.message
                        : `This place could not be ${wasStarred ? 'unstarred' : 'starred'}.`,
                })
            }
        } finally {
            if (!controller.signal.aborted) {
                setUpdatingStarLocationIDs((current) => {
                    const next = new Set(current)
                    next.delete(location.locationID)
                    return next
                })
            }

            if (starActionRequestsRef.current.get(location.locationID) === controller) {
                starActionRequestsRef.current.delete(location.locationID)
            }
        }
    }

    function retryLoadStars() {
        setIsStarsLoading(true)
        setStarsLoadError('')
        setStarsReloadKey((key) => key + 1)
    }

    const selectedLocationIsStarred = selectedLocation
        ? starredLocationIDs.has(selectedLocation.locationID)
        : false
    const selectedLocationStarIsUpdating = selectedLocation
        ? updatingStarLocationIDs.has(selectedLocation.locationID)
        : false
    const selectedStarLoadingAction: 'check' | 'star' | 'unstar' | undefined = isStarsLoading
        ? 'check'
        : selectedLocationStarIsUpdating
            ? selectedLocationIsStarred ? 'unstar' : 'star'
            : undefined
    return (
        <section className="relative h-svh min-h-svh w-full" aria-label="Map">
            <div className="relative isolate h-svh min-h-svh w-full overflow-hidden bg-[var(--soft-surface)]">
                <div
                    ref={mapElementRef}
                    className="z-[1] h-svh min-h-svh w-full font-[inherit] [&_.leaflet-control-attribution]:bg-white/80 [&_.leaflet-control-attribution]:text-[9px] [&_.leaflet-control-attribution]:text-[#6f7a88] [&_.weather-map-marker-wrap]:border-0 [&_.weather-map-marker-wrap]:bg-transparent [&_.weather-map-marker]:relative [&_.weather-map-marker]:block [&_.weather-map-marker]:h-9 [&_.weather-map-marker]:w-9 [&_.weather-map-marker]:rotate-[-45deg] [&_.weather-map-marker]:rounded-[50%_50%_50%_8px] [&_.weather-map-marker]:border-4 [&_.weather-map-marker]:border-white [&_.weather-map-marker]:bg-[var(--accent)] [&_.weather-map-marker]:shadow-[0_7px_16px_rgba(16,91,147,0.3)] [&_.weather-map-marker_span]:absolute [&_.weather-map-marker_span]:inset-[9px] [&_.weather-map-marker_span]:rounded-full [&_.weather-map-marker_span]:border-[3px] [&_.weather-map-marker_span]:border-white"
                    aria-label="Interactive weather location map"
                />

                {!mapReady && !mapError && (
                    <div className="absolute inset-0 z-[3] flex flex-col items-center justify-center gap-2 bg-[radial-gradient(ellipse_at_0%_0%,rgba(142,207,255,0.58)_0,rgba(205,235,255,0.34)_32%,transparent_62%),radial-gradient(ellipse_at_100%_0%,rgba(255,210,72,0.56)_0,rgba(255,226,148,0.3)_25%,transparent_50%),radial-gradient(ellipse_at_0%_100%,rgba(247,174,207,0.6)_0,rgba(251,215,229,0.34)_27%,transparent_55%),radial-gradient(ellipse_at_100%_100%,rgba(12,35,70,0.5)_0,rgba(35,61,101,0.26)_28%,transparent_56%),#f7f8fa] p-[30px] text-center text-[var(--ink)]" role="status">
                        <span className="h-[34px] w-[34px] animate-spin rounded-full border-[3px] border-[#cfe8fa] border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                        <strong className="mt-1.5 text-[1.05rem]">Preparing your map</strong>
                        <small className="max-w-[450px] text-[0.83rem] leading-6 text-[var(--muted)]">Loading the map and location tools…</small>
                    </div>
                )}

                {mapError && (
                    <div className="absolute inset-0 z-[3] flex flex-col items-center justify-center gap-2 bg-[#f7f8fa] p-[30px] text-center text-[var(--ink)]" role="alert">
                        <span className="grid h-10 w-10 place-items-center rounded-[14px] bg-[#fdebed] text-xl font-extrabold text-[#bd4c54]" aria-hidden="true">!</span>
                        <strong className="mt-1.5 text-[1.05rem]">Map unavailable</strong>
                        <small className="max-w-[450px] text-[0.83rem] leading-6 text-[var(--muted)]">{mapError}</small>
                    </div>
                )}

                {mapReady && !mapError && (
                    <div className="absolute top-6 left-[172px] z-[500] w-[min(760px,calc(100vw-212px))] min-[1025px]:max-[1100px]:top-5 min-[1025px]:max-[1100px]:left-[136px] min-[1025px]:max-[1100px]:w-[min(680px,calc(100vw-160px))] max-[1025px]:top-4 max-[1025px]:left-4 max-[1025px]:w-[calc(100vw-32px)] max-[520px]:top-3 max-[520px]:left-3 max-[520px]:w-[calc(100vw-24px)]">
                        <form className="glass-surface flex min-h-[72px] items-center gap-3 rounded-[28px] border border-white/85 bg-white/90 p-[9px] text-[var(--ink)] shadow-[0_18px_46px_rgba(45,101,148,0.18)] backdrop-blur-[18px] backdrop-saturate-125 max-[1100px]:min-h-16 max-[1100px]:gap-[9px] max-[1100px]:rounded-3xl max-[1100px]:p-2 max-lg:min-h-[58px] max-lg:gap-[7px] max-lg:rounded-[22px] max-lg:p-[7px] max-[520px]:gap-1.5 max-[520px]:rounded-[20px] max-[520px]:p-1.5" role="search" onSubmit={searchLocation}>
                            <div className="glass-inset flex min-h-[54px] min-w-0 flex-1 items-center gap-3.5 rounded-[21px] border border-[#d7e5f2] bg-white/75 py-0 pr-[9px] pl-5 shadow-[inset_0_1px_2px_rgba(60,105,143,0.04)] focus-within:border-[#1596f594] focus-within:shadow-[0_0_0_3px_rgba(21,150,245,0.1)] max-[1100px]:min-h-12 max-[1100px]:gap-[11px] max-[1100px]:rounded-[18px] max-[1100px]:pl-4 max-lg:min-h-11 max-lg:gap-[9px] max-lg:rounded-2xl max-lg:pr-1.5 max-lg:pl-[13px] max-[520px]:gap-[7px] max-[520px]:pl-[11px]">
                                <Search className="h-[25px] w-[25px] shrink-0 stroke-[2.2] text-[var(--accent)] max-[1100px]:h-[22px] max-[1100px]:w-[22px] max-lg:h-[21px] max-lg:w-[21px]" aria-hidden="true" />
                                <input
                                    id="map-location-search"
                                    name="location"
                                    type="search"
                                    value={searchQuery}
                                    placeholder="Search place or address"
                                    aria-label="Search place or address"
                                    className="min-w-0 flex-1 border-0 bg-transparent text-base font-semibold text-[var(--ink)] outline-0 placeholder:font-normal placeholder:text-[var(--muted)] [&::-webkit-search-cancel-button]:hidden max-[1100px]:text-sm max-[760px]:text-[0.86rem]"
                                    onChange={(event) => {
                                        setSearchQuery(event.target.value)
                                        setSearchResults([])
                                    }}
                                />
                                {searchQuery && (
                                    <button
                                        className="glass-inset grid min-h-[38px] w-[38px] basis-[38px] cursor-pointer place-items-center rounded-full border-0 p-0 text-[var(--ink)] transition-transform active:scale-90 max-lg:min-h-8 max-lg:w-8 max-lg:basis-8 [&_svg]:h-5 [&_svg]:w-5 [&_svg]:stroke-[2.2]"
                                        type="button"
                                        aria-label="Clear search"
                                        onClick={clearSearch}
                                    >
                                        <X aria-hidden="true" />
                                    </button>
                                )}
                            </div>
                            <button
                                className="min-h-[54px] shrink-0 cursor-pointer rounded-[20px] border border-[var(--active-border)] [background:var(--action-background)] px-[25px] text-[0.94rem] font-extrabold text-[var(--action-ink)] shadow-[0_8px_20px_rgba(21,50,84,0.24)] transition-[background,transform] hover:not-disabled:[background:var(--action-hover-background)] active:not-disabled:scale-[0.95] disabled:cursor-not-allowed disabled:opacity-60 max-[1100px]:min-h-12 max-[1100px]:rounded-[17px] max-[1100px]:px-5 max-[1100px]:text-[0.86rem] max-lg:min-h-11 max-lg:rounded-[15px] max-lg:px-3.5 max-lg:text-[0.8rem] max-[520px]:min-w-[60px] max-[520px]:px-2.5"
                                type="submit"
                                disabled={isSearching || !searchQuery.trim()}
                            >
                                {isSearching ? 'Searching' : 'Search'}
                            </button>
                        </form>

                        {searchError && <p className="mt-2.5 w-fit max-w-full rounded-xl bg-[#ffeff1f5] px-3 py-[9px] text-[0.78rem] font-extrabold text-[#9f3340] shadow-[0_10px_24px_rgba(32,43,60,0.12)]">{searchError}</p>}

                        {searchResults.length > 0 && (
                            <ul className="glass-surface-strong mt-2 max-h-[min(420px,calc(100vh-110px))] list-none overflow-y-auto rounded-2xl border border-[#d2dde5f5] bg-white/95 p-[7px] shadow-[0_18px_42px_rgba(32,43,60,0.16)] backdrop-blur-2xl max-[1025px]:max-h-[min(50svh,420px)] [&_li+li]:border-t [&_li+li]:border-[#edf1f4]" aria-label="Location search results">
                                {searchResults.map((result) => (
                                    <li key={`${result.address}-${result.latitude}-${result.longitude}`}>
                                        <button className="grid w-full cursor-pointer gap-1.5 rounded-[10px] border-0 bg-transparent px-[15px] py-3.5 text-left text-[var(--ink)] transition-transform hover:bg-white/10 active:scale-[0.98] focus-visible:bg-white/10 focus-visible:outline-0" type="button" onClick={() => selectSearchResult(result)}>
                                            <strong className="text-base font-medium">{result.name}</strong>
                                            <span className="text-[0.88rem] leading-[1.35] text-[var(--muted)]">{result.address}</span>
                                            <small className="text-[0.78rem] text-[var(--muted)]">
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
                        <div className="absolute bottom-[30px] left-[136px] z-[500] grid gap-2 max-[1025px]:bottom-[94px] max-[1025px]:left-4 max-[520px]:bottom-[90px] max-[520px]:left-3 max-[700px]:[.weather-modal~&]:hidden">
                            {locationError && <p className="m-0 max-w-[260px] rounded-xl bg-[#ffeff1f5] px-3 py-[9px] text-[0.76rem] font-bold text-[#9f3340] shadow-[0_8px_22px_rgba(32,43,60,0.12)]" role="alert">{locationError}</p>}
                            <button className="glass-surface inline-flex min-h-[52px] cursor-pointer items-center justify-center gap-2.5 rounded-[18px] border border-white/90 px-[22px] text-[0.9rem] font-extrabold text-[var(--ink)] transition-transform active:not-disabled:scale-[0.94] disabled:cursor-wait disabled:opacity-60 max-[760px]:min-h-[46px] max-[760px]:rounded-2xl max-[760px]:px-4 max-[760px]:text-[0.8rem] max-[520px]:px-3.5 [&_svg]:h-[21px] [&_svg]:w-[21px] [&_svg]:text-[var(--accent)]" type="button" onClick={goToCurrentLocation} disabled={isLocating}>
                                {isLocating
                                    ? <span className="h-[21px] w-[21px] animate-spin rounded-full border-2 border-[#cfe8fa] border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                                    : <Navigation2 aria-hidden="true" />}
                                <span>{isLocating ? 'Locating' : 'My location'}</span>
                            </button>
                        </div>

                        <div className="glass-surface absolute right-6 bottom-[30px] z-[500] overflow-hidden rounded-[17px] border border-[#d2dde5eb] bg-white/95 shadow-[0_14px_34px_rgba(32,43,60,0.15)] backdrop-blur-2xl min-[1025px]:max-[1100px]:right-5 max-[1025px]:right-4 max-[1025px]:bottom-[94px] max-[1025px]:rounded-[15px] max-[520px]:right-3 max-[520px]:bottom-[90px] [&_button+button]:border-t [&_button+button]:border-[#dde7ef]" aria-label="Map zoom controls">
                            <button className="grid h-[52px] w-[52px] cursor-pointer place-items-center border-0 bg-transparent p-0 text-[var(--ink)] transition-[transform,color,background-color] hover:bg-white/10 hover:text-[var(--accent)] active:scale-90 max-[760px]:h-[46px] max-[760px]:w-[46px] [&_svg]:h-[25px] [&_svg]:w-[25px] [&_svg]:stroke-[2.2]" type="button" aria-label="Zoom in" onClick={() => mapRef.current?.zoomIn()}>
                                <Plus aria-hidden="true" />
                            </button>
                            <button className="grid h-[52px] w-[52px] cursor-pointer place-items-center border-0 bg-transparent p-0 text-[var(--ink)] transition-[transform,color,background-color] hover:bg-white/10 hover:text-[var(--accent)] active:scale-90 max-[760px]:h-[46px] max-[760px]:w-[46px] [&_svg]:h-[25px] [&_svg]:w-[25px] [&_svg]:stroke-[2.2]" type="button" aria-label="Zoom out" onClick={() => mapRef.current?.zoomOut()}>
                                <Minus aria-hidden="true" />
                            </button>
                        </div>
                    </>
                )}

                {selectedLocation && (
                    <aside
                        className="weather-modal glass-surface-strong absolute top-28 right-6 z-[550] max-h-[calc(100svh-136px)] w-[min(900px,calc(100vw-48px))] overflow-y-auto overscroll-contain rounded-[28px] border border-[#d2dde5f5] bg-white/95 p-6 text-[var(--ink)] shadow-[0_24px_56px_rgba(32,43,60,0.2)] backdrop-blur-[18px] min-[1025px]:max-[1100px]:top-[108px] min-[1025px]:max-[1100px]:right-5 min-[1025px]:max-[1100px]:max-h-[calc(100svh-138px)] min-[1025px]:max-[1100px]:w-[min(620px,calc(100vw-156px))] max-[1025px]:fixed max-[1025px]:top-auto max-[1025px]:right-4 max-[1025px]:bottom-[94px] max-[1025px]:left-4 max-[1025px]:max-h-[min(70svh,calc(100svh-190px))] max-[1025px]:w-auto max-[1025px]:rounded-[21px] max-[1025px]:p-[21px] max-[430px]:right-3 max-[430px]:left-3 max-[430px]:p-4"
                        role="dialog"
                        aria-modal="false"
                        aria-labelledby="weather-modal-title"
                    >
                        <button
                            className="glass-inset absolute top-[15px] right-[15px] grid size-9 cursor-pointer place-items-center rounded-[11px] border border-white/55 p-0 text-[var(--muted-strong)] transition-transform active:scale-[0.9] [&_svg]:size-[18px] [&_svg]:stroke-2"
                            type="button"
                            aria-label="Close current weather"
                            onClick={closeWeatherModal}
                        >
                            <X aria-hidden="true" />
                        </button>

                        {selectedLocationIsNormalized && !starsLoadError && (
                            <div className="absolute top-[15px] right-[59px]">
                                <StarButton
                                    isStarred={selectedLocationIsStarred}
                                    loadingAction={selectedStarLoadingAction}
                                    onClick={() => void toggleSelectedLocationStar()}
                                />
                            </div>
                        )}

                        <p className="m-0 mr-24 mb-[7px] text-[0.74rem] font-extrabold tracking-[0.1em] text-[var(--accent)] uppercase">Current weather</p>
                        <h2 className="m-0 mr-24 text-[1.35rem] leading-[1.15] font-extrabold" id="weather-modal-title">{selectedLocation.name}</h2>
                        <p className="mt-[9px] mb-0 text-[0.82rem] leading-[1.45] text-[var(--muted)]">{selectedLocation.address}</p>

                        <div className="mt-[15px] grid gap-2">
                            {selectedLocationIsNormalized && starsLoadError && (
                                <div className="flex items-center justify-between gap-3 rounded-[11px] bg-[#fff0f2] px-3 py-2.5 text-[0.76rem] leading-[1.45] font-semibold text-[#9f3340]" role="alert">
                                    <span>{starsLoadError}</span>
                                    <button
                                        className="shrink-0 cursor-pointer rounded-lg border border-[#efc7ce] bg-white/75 px-2.5 py-1.5 font-extrabold hover:bg-white focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-[#1596f559]"
                                        type="button"
                                        onClick={retryLoadStars}
                                    >
                                        Try again
                                    </button>
                                </div>
                            )}

                            {!selectedLocationIsNormalized && (
                                <p className="m-0 rounded-[11px] bg-[#eef3f7] px-3 py-2.5 text-[0.76rem] leading-[1.45] font-semibold text-[#536275]">
                                    This location could not be verified by Geoapify, so it cannot be starred.
                                </p>
                            )}

                            {starActionError?.locationID === selectedLocation.locationID && (
                                <p className="m-0 rounded-[11px] bg-[#fff0f2] px-3 py-2.5 text-[0.76rem] leading-[1.45] font-semibold text-[#9f3340]" role="alert">
                                    {starActionError.message}
                                </p>
                            )}
                        </div>

                        {isWeatherLoading && (
                            <div className="flex min-h-[190px] flex-col items-center justify-center gap-3.5 text-[0.88rem] text-[var(--muted)]" role="status">
                                <span className="h-[34px] w-[34px] animate-spin rounded-full border-[3px] border-[#cfe8fa] border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                                <span>Loading current weather…</span>
                            </div>
                        )}

                        {weatherError && (
                            <div className="flex min-h-[190px] flex-col items-center justify-center gap-3.5 text-center text-[0.88rem] text-[#9f3340]" role="alert">
                                <span>{weatherError}</span>
                                <button
                                    type="button"
                                    onClick={() => void selectLocation(
                                        selectedLocation,
                                        selectedLocationIsNormalized,
                                    )}
                                >
                                    Try again
                                </button>
                            </div>
                        )}

                        {currentWeather && (
                            <div className="glass-inset relative mt-[22px] grid min-h-[184px] grid-cols-[126px_minmax(0,1fr)_auto] gap-[18px] overflow-hidden rounded-[19px] border border-[#d1e7f9c7] bg-[linear-gradient(135deg,#edf8ff,#f8fbff_54%,#fff5f6)] px-[26px] py-6 max-lg:min-h-0 max-lg:grid-cols-[90px_minmax(0,1fr)] max-lg:gap-4 max-lg:p-5 max-[430px]:grid-cols-[72px_minmax(0,1fr)] max-[430px]:gap-3 max-[430px]:p-3.5">
                                    <div className="relative z-[1] grid self-center place-items-center" aria-hidden="true">
                                        <img className="block h-[120px] w-[120px] object-contain drop-shadow-[0_18px_18px_rgba(45,101,148,0.16)] max-lg:h-[88px] max-lg:w-[88px] max-[430px]:size-[70px]" src={getWeatherArtwork(currentWeather)} alt="" />
                                    </div>

                                    <div className="relative z-[1] min-w-0 self-center">
                                        <p className="m-0 text-[4.15rem] leading-[0.95] font-light tracking-[-0.07em] text-[var(--ink)] max-lg:text-[3.65rem] max-[430px]:text-[3rem]">
                                            {Math.round(currentWeather.temperatureCelsius)}<span className="ml-[5px] align-top text-[0.38em] tracking-normal">°C</span>
                                        </p>
                                        <p className="mt-2 mb-0 text-xl font-extrabold text-[var(--ink)] max-lg:text-base">{currentWeather.condition}</p>
                                        <p className="mt-3.5 mb-0 text-[0.92rem] text-[var(--muted)]">
                                            Feels like{' '}
                                            <strong>{Math.round(currentWeather.apparentTemperatureCelsius)}°C</strong>
                                        </p>
                                    </div>

                                    <div className="relative z-[1] flex flex-col items-end gap-2.5 self-start max-lg:col-span-full max-lg:flex-row max-lg:flex-wrap max-lg:items-center">
                                        <span className={`inline-flex items-center gap-[7px] whitespace-nowrap rounded-full px-3 py-[9px] text-[0.78rem] font-extrabold [&_svg]:h-[17px] [&_svg]:w-[17px] ${currentWeather.daytime ? 'bg-[#fff6d3eb] text-[#9b6a00]' : 'bg-[#e5ebfff0] text-[#566898]'}`}>
                                            {currentWeather.daytime
                                                ? <Sun aria-hidden="true" />
                                                : <Moon aria-hidden="true" />}
                                            {currentWeather.daytime ? 'Day' : 'Night'}
                                        </span>
                                        <span className="inline-flex items-center gap-[7px] whitespace-nowrap rounded-full bg-[#ffe5eef0] px-3 py-[9px] text-[0.78rem] font-extrabold text-[#e44978]">
                                            WMO {currentWeather.weatherCode}
                                        </span>
                                    </div>

                                    <p className="relative z-[1] col-span-full m-0 text-center text-[0.72rem] leading-relaxed italic text-[var(--muted)]">
                                        (*) Weather information may vary slightly from actual conditions.
                                    </p>
                            </div>
                        )}

                        {(currentWeather || !isWeatherLoading) && (
                            <HourlyForecast
                                forecast={hourlyWeather}
                                currentWeather={currentWeather}
                                isLoading={isHourlyWeatherLoading}
                                error={hourlyWeatherError}
                                onRetry={() => void retryHourlyForecast()}
                            />
                        )}

                        {currentWeather && (
                            <div className="mt-3.5 grid grid-cols-[minmax(0,0.95fr)_minmax(360px,1.05fr)] gap-3.5 max-[1280px]:grid-cols-1">
                                <section className="glass-inset min-w-0 rounded-[19px] border border-white/55 p-3.5" aria-labelledby="weather-details-title">
                                    <h3 className="m-0 text-[0.86rem] font-extrabold tracking-[0.06em] text-[var(--muted-strong)] uppercase" id="weather-details-title">Weather details</h3>
                                <div className="glass-grid mt-2.5 grid grid-cols-2 gap-2.5 max-[350px]:grid-cols-1 [&>div]:flex [&>div]:min-h-[88px] [&>div]:min-w-0 [&>div]:items-center [&>div]:gap-[11px] [&>div]:rounded-[17px] [&>div]:border [&>div]:border-[#e5edf3] [&>div]:bg-[linear-gradient(145deg,#fff,#f7fbfe)] [&>div]:p-[13px] max-[430px]:[&>div]:min-h-[78px] max-[430px]:[&>div]:gap-2 max-[430px]:[&>div]:p-2.5 [&_.weather-detail-icon]:grid [&_.weather-detail-icon]:h-[38px] [&_.weather-detail-icon]:w-[38px] [&_.weather-detail-icon]:shrink-0 [&_.weather-detail-icon]:place-items-center [&_.weather-detail-icon]:rounded-full [&_.weather-detail-icon]:bg-[#eaf6ff] [&_.weather-detail-icon]:text-[var(--accent)] [&_.weather-detail-icon--warm]:bg-[#fff0f4] [&_.weather-detail-icon--warm]:text-[#f0527e] [&_.weather-detail-icon_svg]:h-[21px] [&_.weather-detail-icon_svg]:w-[21px] [&_.weather-detail-copy]:min-w-0 [&_.weather-detail-label]:block [&_.weather-detail-label]:text-[0.72rem] [&_.weather-detail-label]:leading-[1.2] [&_.weather-detail-label]:text-[var(--muted)] [&_.weather-detail-value]:mt-1.5 [&_.weather-detail-value]:block [&_.weather-detail-value]:whitespace-nowrap [&_.weather-detail-value]:text-[0.92rem] [&_.weather-detail-value]:leading-[1.2] [&_.weather-detail-value]:font-semibold" role="list" aria-label="Current weather details">
                                    <div role="listitem">
                                        <span className="weather-detail-icon" aria-hidden="true">
                                            <Droplets />
                                        </span>
                                        <div className="weather-detail-copy">
                                            <span className="weather-detail-label">Humidity</span>
                                            <strong className="weather-detail-value">{Math.round(currentWeather.relativeHumidityPercent)}%</strong>
                                        </div>
                                    </div>
                                    <div role="listitem">
                                        <span className="weather-detail-icon" aria-hidden="true">
                                            <CloudRain />
                                        </span>
                                        <div className="weather-detail-copy">
                                            <span className="weather-detail-label">Precipitation</span>
                                            <strong className="weather-detail-value">{currentWeather.precipitationMillimetres.toFixed(1)} mm</strong>
                                        </div>
                                    </div>
                                    <div role="listitem">
                                        <span className="weather-detail-icon" aria-hidden="true">
                                            <Umbrella />
                                        </span>
                                        <div className="weather-detail-copy">
                                            <span className="weather-detail-label">Rain</span>
                                            <strong className="weather-detail-value">{currentWeather.rainMillimetres.toFixed(1)} mm</strong>
                                        </div>
                                    </div>
                                    <div role="listitem">
                                        <span className="weather-detail-icon" aria-hidden="true">
                                            <Cloud />
                                        </span>
                                        <div className="weather-detail-copy">
                                            <span className="weather-detail-label">Cloud cover</span>
                                            <strong className="weather-detail-value">{Math.round(currentWeather.cloudCoverPercent)}%</strong>
                                        </div>
                                    </div>
                                    <div role="listitem">
                                        <span className="weather-detail-icon" aria-hidden="true">
                                            <Wind />
                                        </span>
                                        <div className="weather-detail-copy">
                                            <span className="weather-detail-label">Wind speed</span>
                                            <strong className="weather-detail-value">{Math.round(currentWeather.windSpeedKilometresPerHour)} km/h</strong>
                                        </div>
                                    </div>
                                    <div role="listitem">
                                        <span className="weather-detail-icon weather-detail-icon--warm" aria-hidden="true">
                                            <Compass />
                                        </span>
                                        <div className="weather-detail-copy">
                                            <span className="weather-detail-label">Wind direction</span>
                                            <strong className="weather-detail-value">
                                                {Math.round(currentWeather.windDirectionDegrees)}°{' '}
                                                ({compassDirection(currentWeather.windDirectionDegrees)})
                                            </strong>
                                        </div>
                                    </div>
                                </div>

                                <div className="mt-[18px] grid grid-cols-1 gap-3 border-t border-white/30 pt-[18px] [&>div]:flex [&>div]:min-w-0 [&>div]:gap-2.5 [&>div]:px-0 [&>div>svg]:h-[21px] [&>div>svg]:w-[21px] [&>div>svg]:shrink-0 [&>div>svg]:text-[var(--muted-strong)] [&_span]:block [&_span]:text-[0.7rem] [&_span]:text-[var(--muted)] [&_strong]:mt-[5px] [&_strong]:block [&_strong]:text-[0.8rem] [&_strong]:leading-[1.35] [&_strong]:text-[var(--muted-strong)]">
                                    <div className="pl-0!">
                                        <Clock aria-hidden="true" />
                                        <div>
                                            <span>Timezone</span>
                                            <strong>{currentWeather.timezone}</strong>
                                        </div>
                                    </div>
                                    <div>
                                        <CalendarDays aria-hidden="true" />
                                        <div>
                                            <span>Updated at</span>
                                            <strong>{currentWeather.recordedAt.replace('T', ' ')}</strong>
                                        </div>
                                    </div>
                                    <div>
                                        <MapPin aria-hidden="true" />
                                        <div>
                                            <span>Coordinates</span>
                                            <strong>
                                                {formattedCoordinate(currentWeather.latitude, 'N', 'S')}<br />
                                                {formattedCoordinate(currentWeather.longitude, 'E', 'W')}
                                            </strong>
                                        </div>
                                    </div>
                                </div>
                                </section>
                                <DailyForecast
                                    forecast={dailyWeather}
                                    isLoading={isDailyWeatherLoading}
                                    error={dailyWeatherError}
                                    onRetry={() => void retryDailyForecast()}
                                />
                            </div>
                        )}
                    </aside>
                )}
            </div>
        </section>
    )
}
