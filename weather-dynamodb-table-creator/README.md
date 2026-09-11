# DynamoDB table creator

This standalone Java utility creates the DynamoDB tables used by the backend:

- `Users`, with normalized `email` as its String partition key.
- `StarredLocations`, with `userID` as its String partition key and
  `locationID` as its String sort key.

The application stores `name`, `address`, `latitude`, `longitude`, `starredAt`,
and `weatherAlertsEnabled` as non-key attributes on each starred-location item.

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
mvn -f dynamodb-table-creator/pom.xml compile exec:java
```

The default AWS region is `us-east-1`. To use another region:

```bash
AWS_REGION=ap-southeast-2 mvn -f dynamodb-table-creator/pom.xml compile exec:java
```

Use the optional `USERS_TABLE` and `STARRED_LOCATIONS_TABLE` environment
variables to change the table names. Their defaults are `Users` and
`StarredLocations`.

Run this utility once for each AWS account and region where the application is
deployed. Normal backend image deployments do not need to run it again.

The ECS task role needs `dynamodb:Query`, `dynamodb:GetItem`,
`dynamodb:PutItem`, and `dynamodb:DeleteItem` access to the configured
`StarredLocations` table. The application uses the AWS SDK default credential
provider chain; do not add AWS keys to source code.
