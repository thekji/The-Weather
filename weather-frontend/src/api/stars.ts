import { getAuthorizationHeaders } from './auth'
import { getErrorMessage } from './errorResponse'
import {
    isLocationSearchResult,
    type LocationSearchResult,
} from './locations'

export type CreateStarRequest = LocationSearchResult

export type StarredLocation = LocationSearchResult & {
    starredAt: string
    weatherAlertsEnabled: boolean
}

function isStarredLocation(value: unknown): value is StarredLocation {
    if (!isLocationSearchResult(value)) return false

    const location = value as unknown as Record<string, unknown>
    return typeof location.starredAt === 'string'
        && location.starredAt.trim().length > 0
        && typeof location.weatherAlertsEnabled === 'boolean'
}

async function parseStarredLocation(
    response: Response,
    fallback: string,
): Promise<StarredLocation> {
    let body: unknown

    try {
        body = await response.json()
    } catch {
        throw new Error(fallback)
    }

    if (!isStarredLocation(body)) {
        throw new Error(fallback)
    }

    return body
}

export async function getStarredLocations(
    signal?: AbortSignal,
): Promise<StarredLocation[]> {
    const response = await fetch('/api/stars', {
        headers: {
            Accept: 'application/json',
            ...getAuthorizationHeaders(),
        },
        signal,
    })

    if (response.status !== 200) {
        throw new Error(await getErrorMessage(response, 'Starred places could not be loaded.'))
    }

    let body: unknown

    try {
        body = await response.json()
    } catch {
        throw new Error('The starred places service returned an invalid response.')
    }

    if (!Array.isArray(body) || !body.every(isStarredLocation)) {
        throw new Error('The starred places service returned an invalid response.')
    }

    return body
}

export async function starLocation(
    location: CreateStarRequest,
    signal?: AbortSignal,
): Promise<StarredLocation> {
    const response = await fetch('/api/stars', {
        method: 'POST',
        headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
            ...getAuthorizationHeaders(),
        },
        body: JSON.stringify({
            locationID: location.locationID,
            name: location.name,
            address: location.address,
            latitude: location.latitude,
            longitude: location.longitude,
        }),
        signal,
    })

    if (response.status !== 200 && response.status !== 201) {
        throw new Error(await getErrorMessage(response, 'This place could not be starred.'))
    }

    return parseStarredLocation(
        response,
        'The star service returned an invalid response.',
    )
}

export async function unstarLocation(
    locationID: string,
    signal?: AbortSignal,
): Promise<void> {
    const response = await fetch(`/api/stars/${encodeURIComponent(locationID)}`, {
        method: 'DELETE',
        headers: {
            Accept: 'application/json',
            ...getAuthorizationHeaders(),
        },
        signal,
    })

    if (response.status !== 204) {
        throw new Error(await getErrorMessage(response, 'This place could not be unstarred.'))
    }
}
