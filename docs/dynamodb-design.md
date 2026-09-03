Users - login, register
PK: userID
Attributes:
- email
- name
- passwordHash
- createdAt

GSI: EmailIndex - - find user during login, check whether an email already exists during registration
PK: email (normalizedEmail)

StarredLocations - list locations belonging to one user, star/unstar location
PK: userID
SK: locationID
Attributes:
- name
- address
- latitude
- longitude
- starredAt
- weatherAlertsEnabled

CommunityPosts - query posts for a location, query posts for a user
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

GSI: UserPostsIndex
PK: userID
SK: createdAt

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

PostFeedback - check whether a user already voted on a post
PK: postID
SK: userID
Attributes:
- feedbackType
- createdAt

AlertRules - retrieve predefined weather alert rules
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


AlertState
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