# TheWeather DynamoDB Design

**Status:** Frozen and user-confirmed on 4 September 2026.

Creating the AWS tables, IAM permissions, and repository adapters remains implementation work.

## Users - login and registration
PK: email (normalized)
Attributes:
- userID
- name
- passwordHash
- createdAt

Store `email` in one normalized form, such as trimmed lowercase. Registration uses
a conditional `PutItem` with `attribute_not_exists(email)`, which guarantees that
concurrent requests cannot register the same normalized email twice.

## StarredLocations - list, star, and unstar
PK: userID
SK: locationID
Attributes:
- name
- address
- latitude
- longitude
- starredAt

Use the same `latitude` and `longitude` names returned by normalized Geoapify search. Do not introduce `lat`/`lon` aliases.

## CommunityPosts - create posts and query location/global feeds
PK: postID
Attributes:
- userID
- username
- locationID
- locationName
- address
- latitude
- longitude
- description
- createdAt
- imageKeys
- apiWeather
- weatherAccuracyRating
- helpfulCount
- notHelpfulCount
- feedType

GSI: LocationPostsIndex
PK: locationID
SK: createdAt

GSI: CommunityFeedIndex
PK: feedType (`COMMUNITY`)
SK: createdAt

Query both indexes newest-first with `ScanIndexForward=false` and cursor pagination. `LocationPostsIndex` serves one location and `CommunityFeedIndex` serves the global feed. Do not scan `CommunityPosts` for either feed.

{
    "postID": "post_20260901_001",
    "createdAt": "2026-09-01T21:45:30Z",

    "userID": "user_abc123",
    "username": "Example User",

    "locationID": "geoapify_51f1a7b3c9",
    "locationName": "RMIT University Melbourne City Campus",
    "address": "124 La Trobe Street, Melbourne VIC 3000, Australia",
    "latitude": -37.8076,
    "longitude": 144.9631,

    "description": "It is cloudy and a bit windy here. The weather feels cooler than the actual temperature.",

    "imageKeys": [
        "community-posts/user_abc123/post_20260901_001/weather-photo.webp"
    ],

    "apiWeather": {
        "recordedAt": "2026-09-01T21:45:20Z",
        "weather_code": 3,
        "condition": "Overcast"
    },

    "weatherAccuracyRating": 3,
    "helpfulCount": 4,
    "notHelpfulCount": 1,
    "feedType": "COMMUNITY"
}

Posts store zero to three private-S3 object keys. API responses contain short-lived presigned GET URLs instead of persistent keys.

`weatherAccuracyRating` is the post author's required 1-5 assessment of how well
the server-captured Open-Meteo condition matched what they observed. It remains
independent from community usefulness feedback.

## PostFeedback - one usefulness vote per user and post
PK: postID
SK: userID
Attributes:
- feedbackType (`HELPFUL` or `NOT_HELPFUL`)
- createdAt

No GSI is required. Setting, switching, or removing feedback uses a DynamoDB
transaction so the `PostFeedback` item and both counters remain consistent. A
missing item represents `NONE`; an existing item contains `HELPFUL` or
`NOT_HELPFUL`. The composite key enforces one current vote per user/post pair.
Feed responses batch-read those exact composite keys to calculate nullable
`myFeedback` for the authenticated viewer. Deleting a post queries this
table by `postID` and deletes the returned items; it never scans the table. The
current delete flow removes the `CommunityPosts` item first, then feedback rows,
then S3 images. Removing the post first makes the transaction's post-existence
condition reject any feedback racing with deletion.

There is no separate `Locations`, `WeatherSnapshot`, notification-history, or
daily-summary table.
