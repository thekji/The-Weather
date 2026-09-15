export type WeatherAccuracyRating = 1 | 2 | 3 | 4 | 5

export const weatherAccuracyRatingLabels: Record<WeatherAccuracyRating, string> = {
    1: 'Not at all',
    2: 'Slightly',
    3: 'Somewhat',
    4: 'Mostly',
    5: 'Exactly',
}

export const weatherAccuracyRatingOptions = [1, 2, 3, 4, 5] as const

export function weatherAccuracyRatingLabel(rating: WeatherAccuracyRating): string {
    return weatherAccuracyRatingLabels[rating]
}

export type CommunityPost = {
    postID: string
    userID: string
    username: string
    locationID: string
    locationName: string
    address: string
    latitude: number
    longitude: number
    description: string
    createdAt: string
    apiWeather: { recordedAt: string; weather_code: number; condition: string }
    imageUrls: string[]
    weatherAccuracyRating: WeatherAccuracyRating
    helpfulCount: number
    notHelpfulCount: number
    myFeedback: FeedbackType | null
}

export type FeedbackType = 'HELPFUL' | 'NOT_HELPFUL'

export type FeedbackResponse = {
    postID: string
    myFeedback: FeedbackType | null
    helpfulCount: number
    notHelpfulCount: number
}

export type PostPage = { items: CommunityPost[]; nextCursor: string | null }
