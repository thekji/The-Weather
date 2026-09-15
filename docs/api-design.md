AUTH
POST   /api/auth/register
POST   /api/auth/login

LOCATION
GET    /api/locations/search?q=
GET    /api/locations/reverse?latitude=&longitude=

STARS
GET    /api/stars
POST   /api/stars
DELETE /api/stars/{locationID}

POSTS
GET    /api/locations/{locationID}/posts?limit=&cursor=
POST   /api/locations/{locationID}/posts (multipart/form-data with required weatherAccuracyRating 1-5)
GET    /api/posts?limit=&cursor=
GET    /api/posts/{postID}
DELETE /api/posts/{postID}
PUT    /api/posts/{postID}/feedback ({ "feedbackType": "HELPFUL" | "NOT_HELPFUL" })
DELETE /api/posts/{postID}/feedback

Post responses include `helpfulCount`, `notHelpfulCount`, and the authenticated
viewer's nullable `myFeedback`. Feedback requires JWT authentication, derives
`userID` from the token, rejects self-feedback, and stores at most one mutable
feedback item per user/post pair. `PUT` sets or switches the choice; `DELETE`
removes it.

WEATHER
GET /api/weather?latitude=&longitude=&timezone=
GET /api/weather/hourly?latitude=&longitude=
GET /api/weather/daily?latitude=&longitude=
GET /api/weather/history?latitude=&longitude=&date=&timezone=

ANALYTICS
POST /api/analytics/refresh
GET  /api/analytics/refresh/status
GET  /api/analytics/summary

All analytics endpoints require JWT authentication. `POST /api/analytics/refresh`
returns HTTP 202 with `REFRESHING`, then the client polls the status endpoint until
Glue reports `READY` or `FAILED`. `GET /api/analytics/summary` returns the four
Athena-computed metrics: total posts, average weather accuracy, the normalized
1-5 rating distribution, and the five most active locations. Spring is a
same-origin facade and does not calculate these values locally.
