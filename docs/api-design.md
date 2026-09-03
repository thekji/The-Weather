AUTH
POST   /api/auth/register
POST   /api/auth/login

LOCATION
GET    /api/locations/search?q=

STARS
GET    /api/stars
POST   /api/stars
DELETE /api/stars/{locationID}
PATCH  /api/stars/{locationID}/alerts

POSTS
GET    /api/locations/{locationID}/posts?limit=&cursor=
POST   /api/locations/{locationID}/posts
GET    /api/posts/{postID}
PATCH  /api/posts/{postID}
DELETE /api/posts/{postID}
POST   /api/posts/{postID}/feedback

IMAGE
GET    /api/posts/{postID}/image
PUT    /api/posts/{postID}/image
DELETE /api/posts/{postID}/image

WEATHER
GET /api/weather?latitude=&longitude=&timezone=
GET /api/weather/history?latitude=&longitude=&date=&timezone=

ANALYTICS
GET /api/analytics/summary
