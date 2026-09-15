import type { HttpError } from '../../../utils/httpHelper'
import { httpHelper } from '../../../utils/httpHelper'
import type {
    AnalyticsRefreshResponse,
    AnalyticsRefreshStatus,
    AnalyticsRefreshStatusResponse,
    AnalyticsSummary,
    LocationPostCount,
    RatingDistribution,
} from '../types'

const REFRESH_ENDPOINT = '/api/analytics/refresh'
const REFRESH_STATUS_ENDPOINT = '/api/analytics/refresh/status'
const SUMMARY_ENDPOINT = '/api/analytics/summary'

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

function isNonNegativeInteger(value: unknown): value is number {
    return typeof value === 'number'
        && Number.isSafeInteger(value)
        && value >= 0
}

function isRefreshStatus(value: unknown): value is AnalyticsRefreshStatus {
    return value === 'REFRESHING' || value === 'READY' || value === 'FAILED'
}

function isRefreshResponse(value: unknown): value is AnalyticsRefreshResponse {
    return isRecord(value)
        && value.status === 'REFRESHING'
        && typeof value.message === 'string'
}

function isRefreshStatusResponse(value: unknown): value is AnalyticsRefreshStatusResponse {
    return isRecord(value) && isRefreshStatus(value.status)
}

function isRatingDistribution(value: unknown): value is RatingDistribution {
    return isRecord(value)
        && isNonNegativeInteger(value.rating)
        && value.rating >= 1
        && value.rating <= 5
        && isNonNegativeInteger(value.count)
}

function isLocationPostCount(value: unknown): value is LocationPostCount {
    return isRecord(value)
        && typeof value.locationName === 'string'
        && value.locationName.trim().length > 0
        && isNonNegativeInteger(value.postCount)
}

function hasNormalizedRatingDistribution(value: unknown): value is RatingDistribution[] {
    return Array.isArray(value)
        && value.length === 5
        && value.every((entry, index) => (
            isRatingDistribution(entry) && entry.rating === index + 1
        ))
}

function isAnalyticsSummary(value: unknown): value is AnalyticsSummary {
    if (!isRecord(value)
        || !isNonNegativeInteger(value.totalPosts)
        || !(value.averageWeatherAccuracy === null
            || (typeof value.averageWeatherAccuracy === 'number'
                && Number.isFinite(value.averageWeatherAccuracy)))
        || !hasNormalizedRatingDistribution(value.ratingDistribution)
        || !Array.isArray(value.topLocations)
        || !value.topLocations.every(isLocationPostCount)
        || typeof value.generatedAt !== 'string') {
        return false
    }

    return !Number.isNaN(Date.parse(value.generatedAt))
}

function isHttpError(error: unknown): error is HttpError {
    return error instanceof Error
        && typeof (error as Partial<HttpError>).status === 'number'
}

function isAbortError(error: unknown): boolean {
    return typeof error === 'object'
        && error !== null
        && 'name' in error
        && error.name === 'AbortError'
}

async function withFriendlyFailure<T>(
    request: () => Promise<T>,
    message: string,
): Promise<T> {
    try {
        return await request()
    } catch (error: unknown) {
        if (isAbortError(error) || isHttpError(error)) throw error
        throw new Error(message, { cause: error })
    }
}

export function isAnalyticsDataUnavailable(error: unknown): boolean {
    return isHttpError(error) && error.status === 404
}

export async function refreshAnalytics(
    signal?: AbortSignal,
): Promise<AnalyticsRefreshResponse> {
    const message = 'Could not export the latest analytics data.'
    return withFriendlyFailure(
        () => httpHelper.post<AnalyticsRefreshResponse>(REFRESH_ENDPOINT, undefined, {
            authenticated: true,
            signal,
            fallbackError: message,
            invalidResponseMessage: 'The analytics refresh service returned an invalid response.',
            validate: isRefreshResponse,
        }),
        message,
    )
}

export async function getAnalyticsRefreshStatus(
    signal?: AbortSignal,
): Promise<AnalyticsRefreshStatusResponse> {
    const message = 'Could not update the analytics catalogue.'
    return withFriendlyFailure(
        () => httpHelper.get<AnalyticsRefreshStatusResponse>(REFRESH_STATUS_ENDPOINT, {
            authenticated: true,
            signal,
            fallbackError: message,
            invalidResponseMessage: 'The analytics status service returned an invalid response.',
            validate: isRefreshStatusResponse,
        }),
        message,
    )
}

export async function getAnalyticsSummary(signal?: AbortSignal): Promise<AnalyticsSummary> {
    const message = 'Could not load analytics.'
    return withFriendlyFailure(
        () => httpHelper.get<AnalyticsSummary>(SUMMARY_ENDPOINT, {
            authenticated: true,
            signal,
            fallbackError: message,
            invalidResponseMessage: 'The analytics service returned an invalid response.',
            statusMessages: {
                404: 'No analytics data is available yet.',
            },
            validate: isAnalyticsSummary,
        }),
        message,
    )
}
