export type AnalyticsRefreshStatus = 'REFRESHING' | 'READY' | 'FAILED'

export type AnalyticsRefreshResponse = {
    status: 'REFRESHING'
    message: string
}

export type AnalyticsRefreshStatusResponse = {
    status: AnalyticsRefreshStatus
}

export type Rating = 1 | 2 | 3 | 4 | 5

export type RatingDistribution = {
    rating: Rating
    count: number
}

export type LocationPostCount = {
    locationName: string
    postCount: number
}

export type AnalyticsSummary = {
    totalPosts: number
    averageWeatherAccuracy: number | null
    ratingDistribution: RatingDistribution[]
    topLocations: LocationPostCount[]
    generatedAt: string
}
