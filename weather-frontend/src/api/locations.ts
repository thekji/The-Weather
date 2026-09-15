import { httpHelper } from '../utils/httpHelper'

export type LocationSearchResult = {
    locationID: string
    name: string
    address: string
    longitude: number
    latitude: number
}

export function isLocationSearchResult(value: unknown): value is LocationSearchResult {
    if (typeof value !== 'object' || value === null) return false

    const location = value as Record<string, unknown>
    return typeof location.locationID === 'string'
        && location.locationID.trim().length > 0
        && typeof location.name === 'string'
        && location.name.trim().length > 0
        && typeof location.address === 'string'
        && location.address.trim().length > 0
        && typeof location.latitude === 'number'
        && Number.isFinite(location.latitude)
        && location.latitude >= -90
        && location.latitude <= 90
        && typeof location.longitude === 'number'
        && Number.isFinite(location.longitude)
        && location.longitude >= -180
        && location.longitude <= 180
}

export async function reverseGeocode(
    latitude: number,
    longitude: number,
    signal?: AbortSignal,
): Promise<LocationSearchResult> {
    const searchParams = new URLSearchParams({
        latitude: String(latitude),
        longitude: String(longitude),
    })
    return httpHelper.get<LocationSearchResult>(`/api/locations/reverse?${searchParams}`, {
        authenticated: true,
        signal,
        fallbackError: 'Could not identify this map location.',
        invalidResponseMessage: 'The location service returned an invalid response.',
        validate: isLocationSearchResult,
    })
}
