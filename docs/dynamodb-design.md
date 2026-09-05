# TheWeather DynamoDB Design

**Status:** Frozen and user-confirmed on 4 September 2026.

Creating the AWS tables, IAM permissions, and repository adapters remains implementation work.

## Users - login and registration
PK: userID
Attributes:
- email
- name
- passwordHash
- createdAt

GSI: EmailIndex - - find user during login, check whether an email already exists during registration
PK: email (normalizedEmail)

Store `email` in one normalized form, such as trimmed lowercase. The assessment implementation checks `EmailIndex` before creation. Document that strict concurrent uniqueness would require a transactional email-identity item if it is not implemented.

## StarredLocations - list, star, unstar, and toggle alerts
PK: userID
SK: locationID
Attributes:
- name
- address
- latitude
- longitude
- starredAt
- weatherAlertsEnabled

Use the same `latitude` and `longitude` names returned by normalized Geoapify search. Do not introduce `lat`/`lon` aliases.

## CommunityPosts - create posts and query a location feed
PK: postID
Attributes:
- userID
- locationID
- locationName
- address
- latitude
- longitude
- description
- createdAt
- imageKeys
- apiWeather
- helpfulCount
- notHelpfulCount

GSI: LocationPostsIndex
PK: locationID
SK: createdAt

Query `LocationPostsIndex` newest-first with `ScanIndexForward=false` and a bounded page size/cursor. Do not scan `CommunityPosts` for the normal feed. `UserPostsIndex` is not part of the frozen P0 design because no required endpoint queries a user's posts.

{
    "postID": "post_20260901_001",
    "createdAt": "2026-09-01T21:45:30Z",

    "userID": "user_abc123",

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
        "temperature_2m": 18.4,
        "relative_humidity_2m": 72,
        "apparent_temperature": 17.1,
        "precipitation_probability": 35,
        "precipitation": 0.2,
        "rain": 0.2,
        "is_day": 0,
        "wind_speed_10m": 16.8,
        "wind_direction_10m": 210,
        "weather_code": 3
    },

    "helpfulCount": 4,
    "notHelpfulCount": 1
}

`precipitation_probability` is a percentage. `precipitation` and `rain` are millimetres.

## PostFeedback - prevent duplicate feedback
PK: postID
SK: userID
Attributes:
- feedbackType
- createdAt

Use a conditional write/transaction with the post counter update so a duplicate request cannot incorrectly increment counters. If vote changes are not implemented, return a controlled conflict.

## AlertRules - retrieve predefined weather alert rules
PK: alertRuleID
Attributes:
- alertType
- metric
- operator
- threshold
- enabled

{
    "alertRuleID": "rule_high_uv_001",
    "alertType": "HIGH_UV",
    "metric": "uv_index_max",
    "operator": ">=",
    "threshold": 7,
    "enabled": true
}


## AlertState
PK: userID
SK: locationID
Attributes:
- lastTriggeredAlerts

{
    "userID": "user_abc123",
    "locationID": "geoapify_51f1a7b3c9",
    "lastTriggeredAlerts": {
        "HIGH_UV": "2026-09-01T03:00:00Z",
        "RAIN_ALERT": "2026-09-01T08:30:00Z",
        "HIGH_TEMPERATURE": "2026-08-31T06:15:00Z"
    }
}

Store one independent cooldown timestamp per alert type. There is no separate `Locations`, `WeatherSnapshot`, alert-history, or daily-summary table.
