import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
    Cloud,
    CloudRain,
    CloudSun,
    Compass,
    Droplets,
    Globe2,
    MapPin,
    Minus,
    Moon,
    Navigation2,
    Plus,
    Search,
    Sun,
    Umbrella,
    UsersRound,
    Wind,
    X,
} from 'lucide-react'
import { useLocation } from 'react-router-dom'
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
import { LocationPosts } from '../components/location-posts/ui/LocationPosts'
import { StarButton } from '../components/StarButton'
import { getWeatherArtwork } from '../components/weatherArtwork'
import { httpHelper } from '../utils/httpHelper'

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

type LocationModalTab = 'weather' | 'posts'

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

function formattedUpdatedDate(recordedAt: string): string {
    const parsed = new Date(recordedAt)
    if (Number.isNaN(parsed.getTime())) return recordedAt.replace('T', ' ')
    return new Intl.DateTimeFormat('en', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
    }).format(parsed)
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
    const searchRequestRef = useRef<AbortController | null>(null)
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
    const [locationModalTab, setLocationModalTab] = useState<LocationModalTab>('weather')
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
        searchRequestRef.current?.abort()
        searchRequestRef.current = null
        const latLng: L.LatLngExpression = [result.latitude, result.longitude]
        mapRef.current?.flyTo(latLng, 18, { duration: 0.8 })
        markerRef.current?.setLatLng(latLng)
        setIsSearching(false)
        setSearchQuery(result.address)
        setSearchResults([])
        setSearchError('')
        setSelectedLocation(result)
        setSelectedLocationIsNormalized(isNormalized)
        setLocationModalTab('weather')
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
                const config = await httpHelper.get<MapConfig>('/api/config', {
                    authenticated: true,
                    fallbackError: 'Map configuration request failed.',
                })
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

                    searchRequestRef.current?.abort()
                    searchRequestRef.current = null
                    reverseGeocodeRequestRef.current?.abort()
                    weatherRequestRef.current?.abort()
                    hourlyWeatherRequestRef.current?.abort()
                    dailyWeatherRequestRef.current?.abort()
                    const controller = new AbortController()
                    reverseGeocodeRequestRef.current = controller
                    setIsSearching(false)
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
            searchRequestRef.current?.abort()
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

        searchRequestRef.current?.abort()
        const controller = new AbortController()
        searchRequestRef.current = controller
        closeWeatherModal()
        setIsSearching(true)
        setSearchError('')
        setSearchResults([])

        try {
            const searchParams = new URLSearchParams({ q: query })
            const results = await httpHelper.get<LocationSearchResult[]>(
                `/api/locations/search?${searchParams}`,
                {
                    authenticated: true,
                    signal: controller.signal,
                    fallbackError: 'Search failed. Please try another place.',
                    invalidResponseMessage: 'The location service returned an invalid response.',
                    validate: (value): value is LocationSearchResult[] => (
                        Array.isArray(value) && value.every(isLocationSearchResult)
                    ),
                },
            )

            if (controller.signal.aborted) return

            if (results.length === 0) {
                throw new Error('No matching place found.')
            }

            setSearchResults(results)
        } catch (error) {
            if (!controller.signal.aborted) {
                setSearchError(error instanceof Error ? error.message : 'Search failed. Please try another place.')
            }
        } finally {
            if (searchRequestRef.current === controller) {
                searchRequestRef.current = null
                setIsSearching(false)
            }
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
        setLocationModalTab('weather')
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
        searchRequestRef.current?.abort()
        searchRequestRef.current = null
        setIsSearching(false)
        setSearchError('')
        setSearchResults([])
        setSearchQuery('')
    }

    function goToCurrentLocation() {
        searchRequestRef.current?.abort()
        searchRequestRef.current = null
        setIsSearching(false)
        setSearchResults([])
        setSearchError('')
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
    const matchingCurrentHour = currentWeather && hourlyWeather
        ? hourlyWeather.hours.find((hour) =>
            hour.time.slice(0, 13) === currentWeather.recordedAt.slice(0, 13)) ?? null
        : null
    return (
        <section className="relative h-dvh min-h-0 w-full" aria-label="Map">
            <div className="relative isolate h-dvh min-h-0 w-full overflow-hidden bg-[var(--soft-surface)]">
                <div
                    ref={mapElementRef}
                    className="weather-map z-[1] h-dvh min-h-0 w-full font-[inherit] [&_.leaflet-control-attribution]:bg-white/80 [&_.leaflet-control-attribution]:text-[9px] [&_.leaflet-control-attribution]:text-[#6f7a88] [&_.weather-map-marker-wrap]:border-0 [&_.weather-map-marker-wrap]:bg-transparent [&_.weather-map-marker]:relative [&_.weather-map-marker]:block [&_.weather-map-marker]:h-9 [&_.weather-map-marker]:w-9 [&_.weather-map-marker]:rotate-[-45deg] [&_.weather-map-marker]:rounded-[50%_50%_50%_8px] [&_.weather-map-marker]:border-4 [&_.weather-map-marker]:border-white [&_.weather-map-marker]:bg-[var(--accent)] [&_.weather-map-marker]:shadow-[0_7px_16px_rgba(16,91,147,0.3)] [&_.weather-map-marker_span]:absolute [&_.weather-map-marker_span]:inset-[9px] [&_.weather-map-marker_span]:rounded-full [&_.weather-map-marker_span]:border-[3px] [&_.weather-map-marker_span]:border-white"
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
                    <div className="absolute top-6 left-[188px] z-[500] w-[min(760px,calc(100vw-228px))] min-[1025px]:max-[1100px]:top-5 min-[1025px]:max-[1100px]:left-[164px] min-[1025px]:max-[1100px]:w-[min(680px,calc(100vw-188px))] max-[1025px]:top-4 max-[1025px]:left-4 max-[1025px]:w-[calc(100vw-32px)] max-[520px]:top-3 max-[520px]:left-3 max-[520px]:w-[calc(100vw-24px)]">
                        <form className="flex min-h-[72px] items-center gap-3 rounded-[28px] border border-[var(--line)] bg-[var(--surface)] p-[9px] text-[var(--ink)] shadow-md max-[1100px]:min-h-16 max-[1100px]:gap-[9px] max-[1100px]:rounded-3xl max-[1100px]:p-2 max-lg:min-h-[58px] max-lg:gap-[7px] max-lg:rounded-[22px] max-lg:p-[7px] max-[520px]:gap-1.5 max-[520px]:rounded-[20px] max-[520px]:p-1.5" role="search" onSubmit={searchLocation}>
                            <div className="flex min-h-[54px] min-w-0 flex-1 items-center gap-3.5 rounded-[21px] border border-[var(--line)] bg-[var(--soft-surface)] py-0 pr-[9px] pl-5 focus-within:border-[var(--accent)] focus-within:shadow-[0_0_0_3px_rgba(21,150,245,0.1)] max-[1100px]:min-h-12 max-[1100px]:gap-[11px] max-[1100px]:rounded-[18px] max-[1100px]:pl-4 max-lg:min-h-11 max-lg:gap-[9px] max-lg:rounded-2xl max-lg:pr-1.5 max-lg:pl-[13px] max-[520px]:gap-[7px] max-[520px]:pl-[11px]">
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
                                        searchRequestRef.current?.abort()
                                        searchRequestRef.current = null
                                        setIsSearching(false)
                                        setSearchQuery(event.target.value)
                                        setSearchResults([])
                                    }}
                                />
                                {searchQuery && (
                                    <button
                                        className="grid min-h-[38px] w-[38px] basis-[38px] cursor-pointer place-items-center rounded-full border border-[var(--line)] bg-[var(--surface)] p-0 text-[var(--ink)] transition-transform active:scale-90 max-lg:min-h-8 max-lg:w-8 max-lg:basis-8 [&_svg]:h-5 [&_svg]:w-5 [&_svg]:stroke-[2.2]"
                                        type="button"
                                        aria-label="Clear search"
                                        onClick={clearSearch}
                                    >
                                        <X aria-hidden="true" />
                                    </button>
                                )}
                            </div>
                            <button
                                className="min-h-[54px] shrink-0 cursor-pointer rounded-[20px] border-0 bg-[var(--action-background)] hover:bg-[var(--action-hover-background)] px-[25px] text-[0.94rem] font-extrabold text-[var(--action-ink)] shadow-sm transition-all active:not-disabled:scale-[0.95] disabled:cursor-not-allowed disabled:opacity-60 max-[1100px]:min-h-12 max-[1100px]:rounded-[17px] max-[1100px]:px-5 max-[1100px]:text-[0.86rem] max-lg:min-h-11 max-lg:rounded-[15px] max-lg:px-3.5 max-lg:text-[0.8rem] max-[520px]:min-w-[60px] max-[520px]:px-2.5"
                                type="submit"
                                disabled={isSearching || !searchQuery.trim()}
                            >
                                {isSearching ? 'Searching' : 'Search'}
                            </button>
                        </form>

                        {searchError && <p className="mt-2.5 w-fit max-w-full rounded-xl bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 px-3 py-[9px] text-[0.78rem] font-extrabold text-red-600 dark:text-red-300 shadow-sm">{searchError}</p>}

                        {searchResults.length > 0 && (
                            <ul className="mt-2 max-h-[min(420px,calc(100vh-110px))] list-none overflow-y-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-[7px] shadow-md max-[1025px]:max-h-[min(50svh,420px)] [&_li+li]:border-t [&_li+li]:border-[var(--line)]" aria-label="Location search results">
                                {searchResults.map((result) => (
                                    <li key={`${result.address}-${result.latitude}-${result.longitude}`}>
                                        <button className="grid w-full cursor-pointer gap-1.5 rounded-[10px] border-0 bg-transparent px-[15px] py-3.5 text-left text-[var(--ink)] transition-colors hover:bg-[var(--soft-surface)] active:scale-[0.98] focus-visible:bg-[var(--soft-surface)] focus-visible:outline-0" type="button" onClick={() => selectSearchResult(result)}>
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
                        <div className="map-bottom-control absolute bottom-[30px] left-[164px] z-[500] grid gap-2 max-[1025px]:left-4 max-[520px]:left-3 max-[700px]:[.weather-modal~&]:hidden">
                            {locationError && <p className="m-0 max-w-[260px] rounded-xl bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 px-3 py-[9px] text-[0.76rem] font-bold text-red-600 dark:text-red-300 shadow-sm" role="alert">{locationError}</p>}
                            <button className="inline-flex min-h-[52px] cursor-pointer items-center justify-center gap-2.5 rounded-[18px] border border-[var(--line)] bg-[var(--surface)] px-[22px] text-[0.9rem] font-extrabold text-[var(--ink)] shadow-md transition-all hover:bg-[var(--soft-surface)] active:not-disabled:scale-[0.94] disabled:cursor-wait disabled:opacity-60 max-[760px]:min-h-[46px] max-[760px]:rounded-2xl max-[760px]:px-4 max-[760px]:text-[0.8rem] max-[520px]:px-3.5 [&_svg]:h-[21px] [&_svg]:w-[21px] [&_svg]:text-[var(--accent)]" type="button" onClick={goToCurrentLocation} disabled={isLocating}>
                                {isLocating
                                    ? <span className="h-[21px] w-[21px] animate-spin rounded-full border-2 border-[#cfe8fa] border-t-[var(--accent)] motion-reduce:animate-none" aria-hidden="true" />
                                    : <Navigation2 aria-hidden="true" />}
                                <span>{isLocating ? 'Locating' : 'My location'}</span>
                            </button>
                        </div>

                        <div className="map-bottom-control absolute right-6 bottom-[30px] z-[500] overflow-hidden rounded-[17px] border border-[var(--line)] bg-[var(--surface)] shadow-md min-[1025px]:max-[1100px]:right-5 max-[1025px]:right-4 max-[1025px]:rounded-[15px] max-[520px]:right-3 [&_button+button]:border-t [&_button+button]:border-[var(--line)]" aria-label="Map zoom controls">
                            <button className="grid h-[52px] w-[52px] cursor-pointer place-items-center border-0 bg-transparent p-0 text-[var(--ink)] transition-colors hover:bg-[var(--soft-surface)] hover:text-[var(--accent)] active:scale-90 max-[760px]:h-[46px] max-[760px]:w-[46px] [&_svg]:h-[25px] [&_svg]:w-[25px] [&_svg]:stroke-[2.2]" type="button" aria-label="Zoom in" onClick={() => mapRef.current?.zoomIn()}>
                                <Plus aria-hidden="true" />
                            </button>
                            <button className="grid h-[52px] w-[52px] cursor-pointer place-items-center border-0 bg-transparent p-0 text-[var(--ink)] transition-colors hover:bg-[var(--soft-surface)] hover:text-[var(--accent)] active:scale-90 max-[760px]:h-[46px] max-[760px]:w-[46px] [&_svg]:h-[25px] [&_svg]:w-[25px] [&_svg]:stroke-[2.2]" type="button" aria-label="Zoom out" onClick={() => mapRef.current?.zoomOut()}>
                                <Minus aria-hidden="true" />
                            </button>
                        </div>
                    </>
                )}

                {selectedLocation && (
                    <aside
                        className="weather-modal liquid-glass absolute top-28 right-6 z-[550] max-h-[calc(100dvh-136px)] w-[min(900px,calc(100vw-48px))] overflow-y-auto overscroll-contain rounded-[28px] p-6 text-[var(--ink)] shadow-xl min-[1025px]:max-[1100px]:top-[108px] min-[1025px]:max-[1100px]:right-5 min-[1025px]:max-[1100px]:max-h-[calc(100dvh-138px)] min-[1025px]:max-[1100px]:w-[min(620px,calc(100vw-168px))] max-[1025px]:fixed max-[1025px]:top-auto max-[1025px]:right-4 max-[1025px]:bottom-[var(--mobile-nav-clearance)] max-[1025px]:left-4 max-[1025px]:max-h-[min(70dvh,calc(100dvh-var(--mobile-nav-clearance)-96px))] max-[1025px]:w-auto max-[1025px]:rounded-[21px] max-[1025px]:p-[21px] max-[430px]:right-3 max-[430px]:left-3 max-[430px]:p-4"
                        role="dialog"
                        aria-modal="false"
                        aria-labelledby="weather-modal-title"
                    >
                        <button
                            className="absolute top-[15px] right-[15px] grid size-9 cursor-pointer place-items-center rounded-[11px] border border-[var(--line)] bg-[var(--soft-surface)] p-0 text-[var(--muted-strong)] transition-transform active:scale-[0.9] [&_svg]:size-[18px] [&_svg]:stroke-2"
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
                        {currentWeather && (
                            <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-[0.7rem] font-semibold text-[var(--muted)]" aria-label="Location weather metadata">
                                <span className="inline-flex items-center gap-1.5"><MapPin className="size-3.5 shrink-0" aria-hidden="true" />{formattedCoordinate(currentWeather.latitude, 'N', 'S')}, {formattedCoordinate(currentWeather.longitude, 'E', 'W')}</span>
                                <span className="hidden text-white/45 min-[560px]:inline" aria-hidden="true">·</span>
                                <span className="inline-flex items-center gap-1.5"><Globe2 className="size-3.5 shrink-0" aria-hidden="true" />{currentWeather.timezone}</span>
                            </div>
                        )}

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

                        <div className="mt-5 grid grid-cols-2 gap-1.5 rounded-[18px] border border-[var(--line)] bg-[var(--soft-surface)] p-1.5" role="tablist" aria-label="Location information">
                            <button
                                className={`inline-flex min-h-11 cursor-pointer items-center justify-center gap-2 rounded-[13px] border px-3 text-[0.8rem] font-extrabold transition-all ${locationModalTab === 'weather' ? 'border-[var(--active-border)] bg-[var(--active-surface)] text-[var(--active-ink)] shadow-sm' : 'border-transparent bg-transparent text-[var(--muted-strong)] hover:bg-[var(--surface-hover)] hover:text-[var(--ink)]'}`}
                                type="button"
                                role="tab"
                                id="location-weather-tab"
                                aria-selected={locationModalTab === 'weather'}
                                aria-controls="location-weather-panel"
                                onClick={() => setLocationModalTab('weather')}
                            >
                                <CloudSun className={`size-[18px] ${locationModalTab === 'weather' ? 'text-[var(--active-icon)]' : ''}`} aria-hidden="true" />
                                Weather
                            </button>
                            <button
                                className={`inline-flex min-h-11 cursor-pointer items-center justify-center gap-2 rounded-[13px] border px-3 text-[0.8rem] font-extrabold transition-all disabled:cursor-not-allowed disabled:opacity-50 ${locationModalTab === 'posts' ? 'border-[var(--active-border)] bg-[var(--active-surface)] text-[var(--active-ink)] shadow-sm' : 'border-transparent bg-transparent text-[var(--muted-strong)] hover:bg-[var(--surface-hover)] hover:text-[var(--ink)]'}`}
                                type="button"
                                role="tab"
                                id="location-posts-tab"
                                aria-selected={locationModalTab === 'posts'}
                                aria-controls="location-posts-panel"
                                disabled={!selectedLocationIsNormalized}
                                onClick={() => setLocationModalTab('posts')}
                            >
                                <UsersRound className="size-[18px]" aria-hidden="true" />
                                Community Posts
                            </button>
                        </div>

                        {locationModalTab === 'weather' && (
                            <div role="tabpanel" id="location-weather-panel" aria-labelledby="location-weather-tab">
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
                            <div className="mt-[22px] grid grid-cols-[minmax(0,1.1fr)_minmax(340px,0.9fr)] items-stretch gap-3.5 max-[1280px]:grid-cols-1">
                                <section className="relative min-w-0 overflow-hidden rounded-[19px] border border-[var(--line)] bg-[var(--soft-surface)] p-4 max-[430px]:p-3.5" aria-labelledby="current-weather-title">
                                    <div>
                                        <h3 className="m-0 text-[0.76rem] font-extrabold tracking-[0.04em] text-[var(--ink)]" id="current-weather-title">Current Weather</h3>
                                        <p className="mt-1 mb-0 text-[0.62rem] font-semibold text-[var(--muted)]">Updated {formattedUpdatedDate(currentWeather.recordedAt)}</p>
                                    </div>
                                    <div className="mt-2 grid grid-cols-[88px_minmax(0,1fr)_auto] items-center gap-4 max-[520px]:grid-cols-[76px_minmax(0,1fr)] max-[430px]:gap-2.5">
                                        <img className="block size-[88px] object-contain drop-shadow-[0_14px_14px_rgba(45,101,148,0.16)] max-[430px]:size-[72px]" src={getWeatherArtwork(currentWeather)} alt="" aria-hidden="true" />
                                        <div className="min-w-0 pl-1">
                                            <p className="m-0 text-[4.4rem] leading-[0.85] font-normal tracking-[-0.06em] text-[var(--ink)] max-[760px]:text-[3.8rem] max-[430px]:text-[3.1rem]">
                                                {Math.round(currentWeather.temperatureCelsius)}<span className="ml-[5px] align-top text-[0.36em] tracking-normal">°C</span>
                                            </p>
                                            <p className="mt-1.5 mb-0 text-[0.84rem] leading-snug font-bold text-[var(--ink)]">{currentWeather.condition}</p>
                                            <p className="mt-1.5 mb-0 text-[0.7rem] text-[var(--muted)]">Feels like <strong>{Math.round(currentWeather.apparentTemperatureCelsius)}°C</strong></p>
                                        </div>
                                        <div className="flex flex-col items-stretch gap-2 max-[520px]:col-span-full max-[520px]:flex-row max-[520px]:flex-wrap">
                                            <span className={`inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-full px-3 py-2 text-[0.68rem] font-extrabold [&_svg]:size-3.5 ${currentWeather.daytime ? 'bg-[#fff6d3eb] text-[#9b6a00]' : 'bg-[#e5ebfff0] text-[#566898]'}`}>
                                                {currentWeather.daytime ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
                                                {currentWeather.daytime ? 'Day' : 'Night'}
                                            </span>
                                            <span className="inline-flex items-center justify-center whitespace-nowrap rounded-full bg-[var(--active-surface)] px-3 py-2 text-[0.68rem] font-extrabold text-[var(--muted-strong)]">WMO {currentWeather.weatherCode}</span>
                                        </div>
                                    </div>

                                    {matchingCurrentHour && (
                                        <div className="mt-3 grid grid-cols-3 gap-2 border-t border-white/40 pt-3 text-[0.66rem] font-semibold text-[var(--muted-strong)] max-[350px]:grid-cols-1">
                                            <span className="inline-flex min-w-0 items-center gap-2"><Umbrella className="size-[18px] shrink-0 text-[var(--accent)]" aria-hidden="true" /><span><strong className="block text-[0.78rem] text-[var(--ink)]">{Math.round(matchingCurrentHour.precipitationProbabilityPercent)}%</strong>Precipitation</span></span>
                                            <span className="inline-flex min-w-0 items-center gap-2"><Sun className="size-[18px] shrink-0 text-[var(--footer-icon)]" aria-hidden="true" /><span><strong className="block text-[0.78rem] text-[var(--ink)]">UV {matchingCurrentHour.uvIndex}</strong>with clouds</span></span>
                                            <span className="inline-flex min-w-0 items-center gap-2"><Sun className="size-[18px] shrink-0 text-[#d94b5c]" aria-hidden="true" /><span><strong className="block text-[0.78rem] text-[#b22e55]">Clear-sky</strong>UV {matchingCurrentHour.uvIndexClearSky}</span></span>
                                        </div>
                                    )}

                                    <p className="mt-3 mb-0 text-[0.6rem] leading-relaxed italic text-[var(--muted)]">(*) Weather information may vary slightly from actual conditions.</p>
                                </section>

                                <section className="min-w-0" aria-label="Weather details">
                                    <div className="grid h-full grid-cols-2 grid-rows-3 gap-2.5 max-[350px]:grid-cols-1 max-[350px]:grid-rows-none [&>div]:flex [&>div]:min-h-[72px] [&>div]:min-w-0 [&>div]:items-center [&>div]:gap-2.5 [&>div]:rounded-[15px] [&>div]:border [&>div]:border-[var(--line)] [&>div]:bg-[var(--soft-surface)] [&>div]:p-3 [&_.weather-detail-icon]:grid [&_.weather-detail-icon]:size-9 [&_.weather-detail-icon]:shrink-0 [&_.weather-detail-icon]:place-items-center [&_.weather-detail-icon]:rounded-full [&_.weather-detail-icon]:bg-[var(--accent-soft)] [&_.weather-detail-icon]:text-[var(--accent)] [&_.weather-detail-icon--warm]:bg-[#fff0f4] [&_.weather-detail-icon--warm]:text-[#f0527e] [&_.weather-detail-icon_svg]:size-5 [&_.weather-detail-copy]:min-w-0 [&_.weather-detail-label]:block [&_.weather-detail-label]:text-[0.64rem] [&_.weather-detail-label]:leading-tight [&_.weather-detail-label]:text-[var(--muted)] [&_.weather-detail-value]:mt-1.5 [&_.weather-detail-value]:block [&_.weather-detail-value]:whitespace-nowrap [&_.weather-detail-value]:text-[0.82rem] [&_.weather-detail-value]:leading-tight [&_.weather-detail-value]:font-extrabold" role="list" aria-label="Current weather details">
                                        <div role="listitem"><span className="weather-detail-icon" aria-hidden="true"><Droplets /></span><div className="weather-detail-copy"><span className="weather-detail-label">Humidity</span><strong className="weather-detail-value">{Math.round(currentWeather.relativeHumidityPercent)}%</strong></div></div>
                                        <div role="listitem"><span className="weather-detail-icon" aria-hidden="true"><Umbrella /></span><div className="weather-detail-copy"><span className="weather-detail-label">Rain</span><strong className="weather-detail-value">{currentWeather.rainMillimetres.toFixed(1)} mm</strong></div></div>
                                        <div role="listitem"><span className="weather-detail-icon" aria-hidden="true"><Cloud /></span><div className="weather-detail-copy"><span className="weather-detail-label">Cloud cover</span><strong className="weather-detail-value">{Math.round(currentWeather.cloudCoverPercent)}%</strong></div></div>
                                        <div role="listitem"><span className="weather-detail-icon" aria-hidden="true"><CloudRain /></span><div className="weather-detail-copy"><span className="weather-detail-label">Precipitation</span><strong className="weather-detail-value">{currentWeather.precipitationMillimetres.toFixed(1)} mm</strong></div></div>
                                        <div role="listitem"><span className="weather-detail-icon" aria-hidden="true"><Wind /></span><div className="weather-detail-copy"><span className="weather-detail-label">Wind speed</span><strong className="weather-detail-value">{Math.round(currentWeather.windSpeedKilometresPerHour)} km/h</strong></div></div>
                                        <div role="listitem"><span className="weather-detail-icon weather-detail-icon--warm" aria-hidden="true"><Compass /></span><div className="weather-detail-copy"><span className="weather-detail-label">Wind direction</span><strong className="weather-detail-value">{Math.round(currentWeather.windDirectionDegrees)}° ({compassDirection(currentWeather.windDirectionDegrees)})</strong></div></div>
                                    </div>
                                </section>
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
                            <div className="mt-3.5">
                                <DailyForecast
                                    forecast={dailyWeather}
                                    isLoading={isDailyWeatherLoading}
                                    error={dailyWeatherError}
                                    onRetry={() => void retryDailyForecast()}
                                />
                            </div>
                        )}
                            </div>
                        )}

                        {locationModalTab === 'posts' && selectedLocationIsNormalized && (
                            <div role="tabpanel" id="location-posts-panel" aria-labelledby="location-posts-tab">
                                <LocationPosts location={selectedLocation} />
                            </div>
                        )}
                    </aside>
                )}
            </div>
        </section>
    )
}
