import type { LocationSearchResult } from '../../../api/locations'
import { httpHelper } from '../../../utils/httpHelper'
import type {
    CommunityPost,
    FeedbackResponse,
    FeedbackType,
    PostPage,
    WeatherAccuracyRating,
} from '../types'

function isFeedbackResponse(value: unknown): value is FeedbackResponse {
    if (typeof value !== 'object' || value === null) return false
    const response = value as Record<string, unknown>
    return typeof response.postID === 'string'
        && (response.myFeedback === null
            || response.myFeedback === 'HELPFUL'
            || response.myFeedback === 'NOT_HELPFUL')
        && typeof response.helpfulCount === 'number'
        && typeof response.notHelpfulCount === 'number'
}

export async function createPost(location: LocationSearchResult, description: string,
    weatherAccuracyRating: WeatherAccuracyRating, images: File[],
    signal?: AbortSignal): Promise<CommunityPost> {
    const data = new FormData()
    data.set('description', description)
    data.set('weatherAccuracyRating', String(weatherAccuracyRating))
    data.set('locationName', location.name)
    data.set('address', location.address)
    data.set('latitude', String(location.latitude))
    data.set('longitude', String(location.longitude))
    images.forEach((image) => data.append('images', image))
    return httpHelper.post<CommunityPost>(
        `/api/locations/${encodeURIComponent(location.locationID)}/posts`,
        data,
        {
            authenticated: true,
            signal,
            fallbackError: 'The community post could not be created.',
        },
    )
}

export async function getLocationPosts(locationID: string, cursor?: string,
    signal?: AbortSignal): Promise<PostPage> {
    const params = new URLSearchParams({ limit: '10' })
    if (cursor) params.set('cursor', cursor)
    return httpHelper.get<PostPage>(
        `/api/locations/${encodeURIComponent(locationID)}/posts?${params}`,
        { authenticated: true, signal, fallbackError: 'Community posts could not be loaded.' },
    )
}

export async function getCommunityPosts(cursor?: string, signal?: AbortSignal): Promise<PostPage> {
    const params = new URLSearchParams({ limit: '10' })
    if (cursor) params.set('cursor', cursor)
    return httpHelper.get<PostPage>(`/api/posts?${params}`, {
        authenticated: true,
        signal,
        fallbackError: 'The community feed could not be loaded.',
    })
}

export async function getCommunityPostCount(userID: string, signal?: AbortSignal): Promise<number> {
    let count = 0
    let cursor: string | null = null

    do {
        const params = new URLSearchParams({ limit: '50' })
        if (cursor) params.set('cursor', cursor)
        const page = await httpHelper.get<PostPage>(`/api/posts?${params}`, {
            signal,
            fallbackError: 'The post count could not be loaded.',
        })
        count += page.items.filter((post) => post.userID === userID).length
        cursor = page.nextCursor
    } while (cursor)

    return count
}

export async function deletePost(postID: string, signal?: AbortSignal): Promise<void> {
    await httpHelper.delete(`/api/posts/${encodeURIComponent(postID)}`, {
        authenticated: true,
        signal,
        fallbackError: 'The post could not be deleted.',
    })
}

export async function setPostFeedback(
    postID: string,
    feedbackType: FeedbackType,
    signal?: AbortSignal,
): Promise<FeedbackResponse> {
    return httpHelper.put<FeedbackResponse>(
        `/api/posts/${encodeURIComponent(postID)}/feedback`,
        { feedbackType },
        {
            authenticated: true,
            signal,
            fallbackError: 'Feedback could not be submitted.',
            invalidResponseMessage: 'The feedback service returned an invalid response.',
            statusMessages: {
                403: 'You cannot provide feedback on your own post.',
            },
            validate: isFeedbackResponse,
        },
    )
}

export async function removePostFeedback(
    postID: string,
    signal?: AbortSignal,
): Promise<FeedbackResponse> {
    return httpHelper.delete<FeedbackResponse>(
        `/api/posts/${encodeURIComponent(postID)}/feedback`,
        {
            authenticated: true,
            signal,
            fallbackError: 'Feedback could not be removed.',
            invalidResponseMessage: 'The feedback service returned an invalid response.',
            statusMessages: {
                403: 'You cannot provide feedback on your own post.',
            },
            validate: isFeedbackResponse,
        },
    )
}
