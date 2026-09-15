import { httpHelper } from '../utils/httpHelper'
import {
    isLocationSearchResult,
    type LocationSearchResult,
} from './locations'

export type CreateStarRequest = LocationSearchResult

export type StarredLocation = LocationSearchResult & {
    starredAt: string
}

function isStarredLocation(value: unknown): value is StarredLocation {
    if (!isLocationSearchResult(value)) return false

    const location = value as unknown as Record<string, unknown>
    return typeof location.starredAt === 'string'
        && location.starredAt.trim().length > 0
}

function isStarredLocationList(value: unknown): value is StarredLocation[] {
    return Array.isArray(value) && value.every(isStarredLocation)
}

export async function getStarredLocations(
    signal?: AbortSignal,
): Promise<StarredLocation[]> {
    return httpHelper.get<StarredLocation[]>('/api/stars', {
        authenticated: true,
        signal,
        fallbackError: 'Starred places could not be loaded.',
        invalidResponseMessage: 'The starred places service returned an invalid response.',
        validate: isStarredLocationList,
    })
}

export async function searchStarredLocations(
    query: string,
    signal?: AbortSignal,
): Promise<StarredLocation[]> {
    const searchParams = new URLSearchParams({ query })
    return httpHelper.get<StarredLocation[]>(`/api/stars?${searchParams}`, {
        authenticated: true,
        signal,
        fallbackError: 'Starred places could not be searched.',
        invalidResponseMessage: 'The starred places service returned an invalid response.',
        validate: isStarredLocationList,
    })
}

export async function starLocation(
    location: CreateStarRequest,
    signal?: AbortSignal,
): Promise<StarredLocation> {
    return httpHelper.post<StarredLocation>('/api/stars', {
        locationID: location.locationID,
        name: location.name,
        address: location.address,
        latitude: location.latitude,
        longitude: location.longitude,
    }, {
        authenticated: true,
        signal,
        fallbackError: 'This place could not be starred.',
        invalidResponseMessage: 'The star service returned an invalid response.',
        validate: isStarredLocation,
    })
}

export async function unstarLocation(
    locationID: string,
    signal?: AbortSignal,
): Promise<void> {
    await httpHelper.delete(`/api/stars/${encodeURIComponent(locationID)}`, {
        authenticated: true,
        signal,
        fallbackError: 'This place could not be unstarred.',
    })
}
