# TheWeather analytics setup

The repository implements the runtime path below. The AWS resources in this
document are one-time deployment configuration; a normal analytics refresh is
started entirely from the application.

```text
CommunityPosts -> Export Lambda -> S3 -> Glue crawler -> Data Catalog
React -> Spring -> API Gateway -> Query Lambda -> Athena -> React
```

## 1. Resource names and S3 layout

Use the existing private analytics bucket. It must be separate from the bucket
that stores community images, have S3 Block Public Access enabled, and retain its
existing encryption policy.

```text
s3://<analytics-bucket>/community-analytics/posts/posts.json
s3://<analytics-bucket>/athena-results/
```

The first prefix contains only the NDJSON source dataset. Athena query output
must use only `athena-results/`. Browser CORS is not needed because neither React
nor the browser calls this bucket or API Gateway directly.

Use these Glue resources:

```text
Database: theweather_analytics
Crawler:  theweather-community-posts-crawler
Source:   s3://<analytics-bucket>/community-analytics/posts/
```

Set the crawler target database to `theweather_analytics` and its recrawl
behavior to **Crawl all folders** on every run. Point it at the source prefix,
not the bucket root, and configure schema deletion behavior to log/ignore
deletions rather than delete the established table. This retains the catalog
schema when a later refresh exports an empty `posts.json`.

Give the crawler a Glue service role that trusts `glue.amazonaws.com`, can list
the analytics bucket and read `community-analytics/posts/*`, and can create or
update catalog tables/partitions in `theweather_analytics`. Use the managed Glue
service-role policy plus a prefix-scoped S3 policy where a narrower custom role
is not practical.

Before the first crawl, create a few realistic `CommunityPosts` through the
application so Glue can infer all eight columns. After the first successful
crawl, record the actual table and normalized column names. The expected columns
are:

```text
postid
userid
locationid
locationname
createdat
weatheraccuracyrating
helpfulcount
nothelpfulcount
```

Do not deploy the query Lambda until the actual names have been verified.

## 2. Build and deploy the Lambda functions

Build the shared shaded JAR:

```bash
cd weather-lambda-functions
mvn clean package
```

The same `target/weather-lambda.jar` is used by two Lambda functions. Deploy the
export/status function first. After its first export/crawl reveals the actual
Glue table and column names, deploy the query function with those names. Use the
same Java runtime already used by the deployed weather handlers. Configure at
least 512 MB memory and a **25-second Lambda timeout** (below the HTTP API's 30-second
integration ceiling), and confirm the runtime supports the project's compiled
Java version. Both analytics handlers stop work early enough to retain five
seconds for a controlled response, and each AWS SDK call is bounded to five
seconds. With the documented 25-second Lambda timeout, analytics work ends after
at most 20 seconds rather than losing the response at the integration boundary.

### Export and refresh-status function

```text
Handler: com.the.weather.lambda.AnalyticsExportHandler
```

Environment:

```text
COMMUNITY_POSTS_TABLE=CommunityPosts
ANALYTICS_BUCKET=<analytics-bucket>
ANALYTICS_KEY=community-analytics/posts/posts.json
GLUE_CRAWLER_NAME=theweather-community-posts-crawler
AWS_REGION=<deployment-region>
```

Grant its execution role only the required actions where Learner Lab permits:

- `dynamodb:Scan` on `CommunityPosts`;
- `s3:PutObject` on `community-analytics/posts/*`;
- `glue:GetCrawler` and `glue:StartCrawler` for the configured crawler;
- normal CloudWatch Logs permissions.

### Athena query function

```text
Handler: com.the.weather.lambda.AnalyticsQueryHandler
```

Environment:

```text
ATHENA_DATABASE=theweather_analytics
ATHENA_TABLE=<actual-crawled-table>
ATHENA_RATING_COLUMN=<actual-rating-column>
ATHENA_LOCATION_COLUMN=<actual-location-column>
ATHENA_OUTPUT_LOCATION=s3://<analytics-bucket>/athena-results/
ATHENA_WORKGROUP=primary
AWS_REGION=<deployment-region>
```

The column variables default to `weatheraccuracyrating` and `locationname`, but
set them explicitly from the inspected catalog schema. Grant the execution role:

- `athena:StartQueryExecution`, `athena:GetQueryExecution`, and
  `athena:GetQueryResults`;
- Glue Data Catalog read access for the configured database and table;
- `s3:ListBucket`/`s3:GetObject` for the analytics source prefix;
- the required S3 read/write access for `athena-results/*` and bucket-location
  lookup;
- normal CloudWatch Logs permissions.

Use role-based credentials. Never put Learner Lab keys in Lambda environment
variables or committed files.

## 3. API Gateway and Spring configuration

Create API Gateway HTTP API payload-v2 integrations:

```text
POST /analytics/refresh         -> AnalyticsExportHandler function
GET  /analytics/refresh/status  -> AnalyticsExportHandler function
GET  /analytics/summary         -> AnalyticsQueryHandler function
```

Set the authorization type on **all three routes** to `AWS_IAM`; do not leave
them as public `NONE` routes. The Spring client signs every downstream request
with SigV4 using the ECS task role and the configured `AWS_REGION`. Grant that
task role `execute-api:Invoke` only for these route ARNs, for example:

```text
arn:aws:execute-api:<region>:<account-id>:<api-id>/<stage>/POST/analytics/refresh
arn:aws:execute-api:<region>:<account-id>:<api-id>/<stage>/GET/analytics/refresh/status
arn:aws:execute-api:<region>:<account-id>:<api-id>/<stage>/GET/analytics/summary
```

Grant API Gateway permission to invoke both functions, restricting each Lambda
permission's `SourceArn` to this API/stage and its intended routes. Unsigned
direct requests should return `403`; only the authenticated Spring facade is a
browser-facing entry point. API Gateway browser CORS is unnecessary because
Spring is the caller. Put the three deployed route URLs in the backend ECS task
definition:

```text
ANALYTICS_REFRESH_URL=<invoke-url>/analytics/refresh
ANALYTICS_REFRESH_STATUS_URL=<invoke-url>/analytics/refresh/status
ANALYTICS_SUMMARY_URL=<invoke-url>/analytics/summary
```

Redeploy the backend and frontend services. The public browser continues to call
only the same-origin `/api/analytics/*` Spring endpoints with its JWT.

### Recommended first deployment sequence

1. Create/verify the private S3 prefixes, Glue database, crawler, and crawler
   role described above.
2. Build the JAR and deploy `AnalyticsExportHandler` with its execution role and
   environment variables.
3. Create realistic `CommunityPosts`, invoke the export function once during
   setup, and wait for the crawler it starts to succeed.
4. Inspect the generated catalog table and all eight normalized column names.
5. Deploy `AnalyticsQueryHandler` using those inspected names, then test its
   summary response directly.
6. Create the three IAM-authorized HTTP API routes and route-scoped Lambda
   invocation permissions; verify unsigned requests are rejected.
7. Add the three API URLs and matching region to the ECS task definition, grant
   the task role route-scoped `execute-api:Invoke`, and redeploy Spring.
8. Deploy the frontend build, sign in, open `/analytics`, and test refresh from
   the application.

## 4. Verification order

1. Confirm `CommunityPosts` contains realistic posts with ratings from 1 to 5.
2. Invoke refresh and verify the exact S3 key contains one JSON object per line,
   only the eight documented fields, and no image or description data.
3. Verify CloudWatch shows the upload completing before `StartCrawler`.
4. Verify the crawler succeeds and inspect the generated table and schema.
5. Run the four SQL statements in Athena once and compare their output with the
   source records.
6. Invoke the query Lambda and API Gateway summary route.
7. Open the deployed Analytics page, capture its initial result, click Refresh
   Analytics, capture the disabled/refreshing state, and verify the new summary.
8. Repeat with an empty `CommunityPosts` table only in a safe test environment;
   verify `0`, `N/A`, five zero rating rows, and no active locations.

Capture the DynamoDB records, export/query Lambda logs, S3 object, crawler run,
catalog schema, Athena executions/query IDs, API Gateway routes, refreshing UI,
and updated dashboard for assessment evidence.

## 5. Athena SQL

The query Lambda builds identifiers from its validated environment configuration
and executes these four calculations in Athena:

```sql
SELECT COUNT(*) AS total_posts
FROM "theweather_analytics"."<actual-table>";

SELECT ROUND(AVG("<actual-rating-column>"), 2) AS average_accuracy
FROM "theweather_analytics"."<actual-table>";

SELECT "<actual-rating-column>" AS rating, COUNT(*) AS post_count
FROM "theweather_analytics"."<actual-table>"
GROUP BY "<actual-rating-column>"
ORDER BY "<actual-rating-column>";

SELECT "<actual-location-column>" AS location_name, COUNT(*) AS post_count
FROM "theweather_analytics"."<actual-table>"
GROUP BY "<actual-location-column>"
ORDER BY post_count DESC
LIMIT 5;
```

No Glue ETL job, manual runtime crawler start, manual runtime Athena query, or
public S3 access is part of the final flow.
