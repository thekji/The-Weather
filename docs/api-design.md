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
GET /api/analytics/summary
