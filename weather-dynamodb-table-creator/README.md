# DynamoDB table creator

This standalone Java utility creates the DynamoDB tables used by the backend:

- `Users`, with normalized `email` as its String partition key.
- `StarredLocations`, with `userID` as its String partition key and
  `locationID` as its String sort key.
- `CommunityPosts`, with `postID` as its String partition key and
  `LocationPostsIndex` / `CommunityFeedIndex` for location and global feeds.
- `PostFeedback`, with `postID` as its String partition key and `userID` as its
  String sort key. No GSI is required.

The application stores `name`, `address`, `latitude`, `longitude`, and
`starredAt` as non-key attributes on each starred-location item.

Existing tables are left unchanged, so the command is safe to run again. The
utility validates both key schemas and stops with an error if an existing table
has conflicting keys.

The utility validates that an existing table already uses `email` as its
partition key. DynamoDB cannot change a table's partition key. If an older
`Users` table still uses `userID`, delete and recreate it only when its data is
disposable, or create a new table and migrate the data.

It is separate from the Spring Boot application and does not run when the app
or a new Docker image starts.

## Requirements

- Java 25
- Maven
- AWS credentials configured locally

## Run

From the repository root:

```bash
mvn -f weather-dynamodb-table-creator/pom.xml compile exec:java
```

The default AWS region is `us-east-1`. To use another region:

```bash
AWS_REGION=ap-southeast-2 mvn -f weather-dynamodb-table-creator/pom.xml compile exec:java
```

Both this utility and the backend support `USERS_TABLE`, `STARRED_LOCATIONS_TABLE`,
`COMMUNITY_POSTS_TABLE`, and `POST_FEEDBACK_TABLE` environment variables. Their
defaults are `Users`, `StarredLocations`, `CommunityPosts`, and `PostFeedback`.

Run this utility once for each AWS account and region where the application is
deployed. Normal backend image deployments do not need to run it again.

The ECS task role needs `dynamodb:Query`, `dynamodb:GetItem`, `dynamodb:BatchGetItem`,
`dynamodb:PutItem`, `dynamodb:UpdateItem`, `dynamodb:DeleteItem`, and
`dynamodb:TransactWriteItems` access to the configured application tables. The
application uses the AWS SDK default credential provider chain; do not add AWS
keys to source code.
