import { getAuthorizationHeaders } from './auth'

export type LocationSearchResult = {
    locationID: string
    name: string
    address: string
    longitude: number
    latitude: number
}

function isLocationSearchResult(value: unknown): value is LocationSearchResult {
    if (typeof value !== 'object' || value === null) return false

    const location = value as Record<string, unknown>
    return typeof location.locationID === 'string'
        && location.locationID.length > 0
        && typeof location.name === 'string'
        && location.name.length > 0
        && typeof location.address === 'string'
        && location.address.length > 0
        && typeof location.latitude === 'number'
        && typeof location.longitude === 'number'
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
    const response = await fetch(`/api/locations/reverse?${searchParams}`, {
        headers: {
            Accept: 'application/json',
            ...getAuthorizationHeaders(),
        },
        signal,
    })

    if (!response.ok) {
        throw new Error('Could not identify this map location.')
    }

    const location: unknown = await response.json()
    if (!isLocationSearchResult(location)) {
        throw new Error('The location service returned an invalid response.')
    }

    return location
}
