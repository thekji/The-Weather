# DynamoDB table creator

This standalone Java utility creates the `Users` DynamoDB table used by login
and registration. The normalized `email` is the partition key, so a conditional
write can guarantee that each email is registered only once.

An existing `Users` table is left unchanged, so the command is safe to run
again.

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

From `weather-backend`:

```bash
mvn -f scripts/dynamodb/pom.xml compile exec:java
```

The default AWS region is `us-east-1`. To use another region:

```bash
AWS_REGION=ap-southeast-2 mvn -f scripts/dynamodb/pom.xml compile exec:java
```

Use the optional `USERS_TABLE` environment variable to change the table name.

Run this utility once for each AWS account and region where the application is
deployed. Normal backend image deployments do not need to run it again.
