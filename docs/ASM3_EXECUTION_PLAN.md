# TheWeather ASM3 - Rubric-Driven Execution Plan

## Project control dates

- Official deadline: **Saturday 12 September 2026, 23:59**.
- Internal implementation and documentation deadline: **Wednesday 9 September 2026, 23:59**.
- Formal testing and defect fixing: **Thursday 10 September and Friday 11 September**.
- Recommended Canvas submission time: **Saturday 12 September, before 12:00**. Do not deliberately wait until 23:59.
- Actionable-plan start: **Tuesday 1 September 2026, 08:00**.
- Build-day working windows for 1-9 September: **08:00-12:00, 13:00-18:00, and 20:00-00:00**.
- Anything dated 30-31 August is historical. This plan does **not** assume that any earlier task was completed; Section 9 explicitly verifies or reschedules it.

This gives **117 scheduled hours** across the nine active build days from 1-9 September, plus 24 scheduled hours for formal testing on 10-11 September. Breaks and context switching mean the real focused time will be lower.

> **Outcome target:** maximize the evidence for 40/40. No timetable can guarantee 40/40 because technology appropriateness, the live demonstration, and Q&A are assessed by the examiner. This plan protects every available rubric mark and removes unnecessary scope.

## 1. Instruction hierarchy and confirmed interpretation

Treat the sources in this order:

1. **The user's current request and pasted update specification** control this revision, including the 1 September start, frozen 9-12 September dates, exact provider boundaries, data models, and removal of stale features.
2. **The current Canvas assignment page and Canvas rubric** are the marking source.
3. **ASSESSMENT_3_S2.pdf** supplies detailed deliverables, the runtime-automation definition, report contents, submission structure, and academic-integrity instructions.
4. **The revised TheWeather planning DOCX** is the project-design source. It is not a new assignment instruction. Where its proposal paragraph or duplicated field list still conflicts with the user's explicit corrections, use the corrections in this plan.
5. **The three old sample architecture documents** are examples of structure and depth only. Their applications, AWS services, developer manuals, user manuals, and 2022 requirements are not mandatory.
6. **This execution plan** is an implementation schedule, not a new assessment requirement.

### Assessment points to clarify with the tutor on 1 September

| Issue | Canvas/current rubric | PDF | Working interpretation |
|---|---|---|---|
| Assessment weight | 40% and 40 points | Cover says 40 marks; overview incorrectly says 50% | Use **40% / 40 points** |
| Cloud implementation criterion | 25 points in the criterion and total | Points column says 25, but rating text says `35~0` | Plan against the **25-point cap**; ask tutor to confirm |
| Canvas rubric range text | Contains a stray `32 to >0.0` line | Contains `35~0` | Treat both as formatting errors unless the tutor says otherwise |
| Demonstration timing | Entirely demo-based during Weeks 10-12 | Says Week 12; its old timeline also says Week 13 | Book the earliest available official slot and confirm the current week |
| Late penalty | Says 10% equals 2 marks per working day | Says 10% of the total mark, which would be 4/40 | Avoid the issue by submitting early; ask for clarification |
| PDF page count | Not relevant | File has 7 pages although later footers say 6/8 and 7/8 | Canvas rubric supplies the complete visible marking breakdown |

The brief explicitly says a developer manual and user manual are **not required**. Do not spend time writing them.

## 2. Verified progress snapshot - 10:00 Friday 4 September 2026

This is the current checkpoint for the schedule. A completed design/prototype is kept separate from a fully integrated application feature.

| Work item | Status | Scope boundary |
|---|---|---|
| AWS permission verification and matrix | **Done - user confirmed** | The planned Learner Lab services were inspected. Keep the user's Console/lab notes as evidence of allowed and denied operations. |
| DynamoDB design freeze | **Done - user confirmed** | Table keys, GSIs, attribute names, and access patterns are frozen in Section 5.5 and `docs/dynamodb-design.md`. Creating the tables, task permissions, and repository adapters is still implementation work. |
| Spring build, React build, Docker/ECR/ECS, ALB and target groups | **Done and deployed** | Both ECS services run behind the ALB; both target groups and the tested frontend/backend routes are healthy. |
| Lambda -> Open-Meteo | **Prototype test passed** | The deployed Java Lambda returns the requested current HCMC fields. It is still hard-coded and is not the parameterized current/hourly/multi-day weather feature. |
| API Gateway -> Lambda | **Prototype test passed** | The deployed route invokes the Lambda independently. |
| Spring -> API Gateway | **Not complete** | This is the current P0-A implementation step. |
| React displays the Lambda result | **Not complete** | Required to close the P0-A walking-skeleton gate. |
| Geoapify search and Geoapify map display | **Next test/implementation slice** | Geoapify replaces Mapbox for both normalized search and interactive map tiles; it is not yet implemented in source. |

The immediate milestone remains the smallest deployed path proving `Browser -> ALB -> React ECS -> Spring ECS -> API Gateway -> Lambda -> Open-Meteo -> React`. The standalone Lambda/API Gateway test is useful evidence, but it does not close that gate until Spring and React invoke it automatically.

### Active work order from 10:00 on 4 September

1. Connect the Spring `/api/weather` facade to the deployed API Gateway route with configuration, query forwarding, bounded timeouts, and controlled errors.
2. Display that returned weather object in React and redeploy both affected ECS services; capture the complete P0-A path.
3. Build a narrow Geoapify proof: Spring search/normalization first, then a React Leaflet map using Geoapify tiles and the selected marker coordinates.
4. Treat the Geoapify proof as a prototype until validation, error handling, authentication, tests, deployment, attribution, and evidence satisfy the corresponding Sep 2-3 exit gates.
5. Resume the remaining dependency order: DynamoDB implementation -> authentication -> stars/weather generalization -> posts/feedback/S3 -> analytics.

This checkpoint changes neither the 9 September implementation/documentation freeze nor the 10-12 September testing/submission dates.

## 3. The 40-point strategy

| Criterion | Marks | What must exist by 9 September |
|---|---:|---|
| Project idea and appropriate technology selection | 2 | Clear problem, beneficiaries, bounded feature set, and a defensible reason for every technology, including rejected alternatives/trade-offs |
| Skill development | 3 | Daily learning log, meaningful commits, test evidence, ability to explain configuration/code, failures solved, IAM, data design, and cloud interactions during Q&A |
| Appropriate and fully automated cloud services/APIs | 25 | Every claimed service is invoked automatically by the UI, application code, or another service; its output is visible; no Console/CLI step is needed during the feature flow |
| Solution architecture document | 10 | All rubric sections, rich and legible diagrams, detailed component interactions, data/API descriptions, references, and final implementation evidence |

### Raw service mark coverage

The following set already exceeds the 25-point implementation cap. More services do not automatically improve the score.

| Category or type | Planned service/API | Published value | Minimum automated proof |
|---|---|---:|---|
| Compute | Lambda | 6 | Weather or analytics UI action invokes a deployed function through the backend and API Gateway |
| Containers | ECS/Fargate | 6 | Browser request reaches both the deployed React/Nginx frontend service and Spring Boot backend service |
| Networking/content delivery and API | API Gateway | 6 | A real UI operation causes the backend to call a deployed route integrated with Lambda |
| Storage | S3 | 3 | Application uploads/retrieves a community image and/or writes the analytics dataset without manual intervention |
| Database | DynamoDB | 3 | UI creates and queries real users, stars, posts, and feedback |
| Load-balancing support | Application Load Balancer | Do not rely on separate marks | ALB automatically routes `/*` to the frontend target group and `/api/*` to the backend target group |
| Analytics | Athena | 3 | The application obtains Athena-derived values and displays them on the dashboard |
| Analytics/catalog | Glue | 3 | Export code or a schedule starts the crawler; the crawler registers the S3 dataset for Athena |
| External API type 1 | Open-Meteo | 2 | UI displays normalized current/forecast weather returned through API Gateway/Lambda; the server also captures a post-time snapshot |
| External API type 2 | Geoapify | 2 | UI search goes through Spring Boot to Geoapify and receives a normalized persistable location; the browser renders the same selected coordinates with Geoapify map tiles |

Only two external API types can be graded. The application deliberately uses exactly **two** external providers: Geoapify for normalized search/geocoding and map tiles, and Open-Meteo for weather. Geoapify is one provider claim even though two of its products are used. ECS, Lambda, and API Gateway count once per service type, not once per container, function, or route. Do not claim two ECS marks because there are two services, and do not claim nested resources such as an ECS/Elastic Beanstalk-created EC2 instance as separate marks. Use API Gateway as the clear Networking and Content Delivery/API-category service; treat ALB as required ECS routing support and do not depend on a separate ALB mark unless the tutor confirms it. ECR, IAM, CloudWatch, EventBridge, and SNS are useful supporting services, but do not rely on them to reach the 25-point cap.

### Definition of "fully implemented and automated"

Do not claim a service until all five statements are true:

1. A user interface action, application code, EventBridge, or another service invokes it.
2. The operation uses the deployed AWS resource, not a mock or local replacement.
3. The result changes application behavior or appears in the UI.
4. A repeatable test and log/screenshot prove the invocation.
5. The report explains why the service is appropriate and identifies at least one alternative or trade-off.

Creating a bucket, table, function, crawler, topic, or cluster in the AWS Console is not implementation evidence on its own.

## 4. Scope freeze

### P0-A - cloud foundation; complete before large features

- Build and deploy separate Dockerized React/Nginx and Spring Boot services on ECS/Fargate.
- Route `/*` to the frontend and `/api/*` to the backend through one ALB with separate `ip` target groups.
- Deploy a minimal Lambda behind API Gateway.
- Make one visible browser operation traverse Browser -> ALB -> React ECS -> Spring ECS -> API Gateway -> Lambda.
- Capture health, target, log, request, and visible-result evidence for that walking skeleton.

### P0-B - core application journey

- Register, log in, log out, and protect user-owned operations.
- Search for locations through Spring Boot -> Geoapify and return only normalized `{locationID, name, address, latitude, longitude}` results.
- Pass the selected latitude/longitude to browser-side Geoapify map tiles for interactive display only.
- View current, hourly, and multi-day weather through Spring Boot -> API Gateway -> Lambda -> Open-Meteo.
- Display deterministic recommendations for rain, UV, wind, temperature, and snow when their required Open-Meteo fields are available.
- Star, unstar, list, and enable/disable alerts for persisted Geoapify locations.
- Create and list community posts whose only user observation field is `description`; attach the exact server-captured `apiWeather` snapshot.
- Attach **zero or one** validated image through direct browser PUT to a private S3 pre-signed URL.
- Mark a post `HELPFUL` or `NOT_HELPFUL` with duplicate-safe counters.

### P0-C - marked data/analytics path and assessment evidence

- Use the frozen DynamoDB tables, keys, and `LocationPostsIndex`; do not use a normal-feed scan.
- Export community/application data from DynamoDB to S3, catalogue it with Glue, and query it with Athena.
- Start/reuse the Glue catalogue step through application code or a schedule so the demonstrated analytics flow is automated, not Console-dependent.
- Display at least three real community metrics, such as total posts, posts by location, and helpful percentage.
- Finish the rubric-aligned architecture report, evidence matrix, test matrix, and reproducible release package.

### P1 - implement only after the complete P0 user journey works

- Historical weather for a validated selected date.
- EventBridge weather checks, system-defined `AlertRules`, per-alert-type cooldowns in `AlertState.lastTriggeredAlerts`, and one SNS email-alert workflow.
- One confirmed demonstration email without adding an alert-history table.

### P2 - cut first if the schedule slips

- Multiple images per post.
- Extra analytics queries/charts and elaborate UI polish.
- Advanced filters or maps, infrastructure-as-code, and nonessential build automation.
- Sophisticated filtering, caching, animations, or visual effects.

The following are **out of scope**, not P2 backlog: a separate map/geocoding provider such as Mapbox, Open-Meteo geocoding, a separate Locations table, a WeatherSnapshot table, an Alerts history table, daily weather-summary emails, continuously storing normal weather queries in S3, user-defined alert thresholds, structured community-condition fields, forecast-versus-observation matching/scoring, comments, followers, messaging, and AI features.

An unfinished claimed feature is worse than a smaller feature that is complete, automated, tested, documented, and easily demonstrated.

## 5. Architecture decisions to freeze on 1 September

### 5.1 Recommended deployment design

Keep the draft's familiar container deployment: Dockerize React/Nginx and Spring Boot separately, push both images to ECR, and run them as two ECS/Fargate services behind one public Application Load Balancer. The frontend and backend must use separate target groups so they can be deployed, health-checked, and scaled independently.

Primary design, subject to Learner Lab permissions:

```text
User's browser
        |
        v
Application Load Balancer
    |       \
    |        \-- priority rule /api/* --> backend target group
    |                                      `--> ECS/Fargate Spring Boot
    |                                             |--> Geoapify search/normalization
    |                                             |--> DynamoDB
    |                                             |--> private S3 pre-signed URLs
    |                                             `--> API Gateway
    |                                                    |--> weather Lambda --> Open-Meteo
    |                                                    `--> analytics Lambda --> Athena
    |
    `-- default rule /* ------------> frontend target group
                                           `--> ECS/Fargate React + Nginx

Browser receives selected latitude/longitude ----------> Geoapify map tiles via Leaflet

EventBridge --> alert Lambda --> StarredLocations + AlertRules + AlertState
                               --> Open-Meteo --> SNS email --> AlertState update
EventBridge/UI --> export Lambda --> DynamoDB community data --> S3 --> Glue --> Athena
```

Freeze these runtime sequences and reproduce them in the report diagrams:

- **Location:** React -> ALB -> Spring -> Geoapify Search/Geocoding API -> Spring normalization -> React; selected coordinates -> Geoapify map tiles/marker via Leaflet.
- **Weather:** React -> ALB -> Spring -> API Gateway -> weather Lambda -> Open-Meteo -> normalized result -> React.
- **Image:** React -> Spring pre-sign -> short-lived URL -> React direct PUT -> private S3; post creation -> Spring-authorized `imageKeys` -> DynamoDB.
- **Post snapshot:** Spring post creation -> API Gateway -> weather Lambda -> Open-Meteo -> exact `apiWeather` -> `CommunityPosts`.
- **Analytics:** DynamoDB community data -> export Lambda -> S3 -> Glue -> Athena; React -> Spring -> API Gateway -> analytics Lambda -> Athena -> React.
- **Alerts:** EventBridge -> alert Lambda -> StarredLocations/Open-Meteo/AlertRules/AlertState -> cooldown -> SNS -> AlertState update.

Required deployment details:

- Create separate frontend and backend ECR repositories, task definitions, ECS services, and target groups.
- Use target type `ip` for both Fargate target groups.
- Frontend container: multi-stage Node build followed by Nginx on port 80.
- Configure Nginx SPA fallback so unknown client-side routes return `index.html`.
- Backend container: Spring Boot on port 8080 with `/actuator/health`.
- Give the `/api/*` listener rule higher priority; make `/*` the default frontend rule.
- In React, use relative URLs such as `/api/stars` so frontend/backend traffic is same-origin and does not require browser CORS configuration.
- Allow the ECS task security groups to receive traffic only from the ALB security group.
- Use an HTTPS ALB listener with a domain and ACM certificate if available. If the assessment lab provides only an ALB HTTP URL, document TLS as a production limitation and never claim that credentials are encrypted in transit.

Why this is defensible:

- It matches existing Docker knowledge and reduces delivery risk during a short sprint.
- It demonstrates a real multi-service container deployment rather than an unused ECS resource.
- The frontend and backend can be released and scaled independently.
- Nginx gives predictable static-file and SPA routing behavior.
- One ALB provides a single application entry point and path-based routing.

The report must acknowledge the honest trade-off: S3/CloudFront would normally be cheaper and simpler for static React files, while ECS was chosen here for a consistent container workflow, independent service deployment, existing skills, and lower schedule risk. Familiarity alone is not the justification; connect it to reliability and completion risk. AWS documents ALB path-based routing for ECS in [Use load balancing to distribute Amazon ECS service traffic](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-load-balancing.html).

### 5.2 Divide responsibilities clearly

Use the Spring Boot ECS backend for:

- authentication and authorization;
- Geoapify location/address/POI search, raw-response handling, normalization, and persisted-location validation;
- users, stars, community posts, feedback, and image/post association;
- DynamoDB writes/queries;
- generating server-owned S3 object keys and short-lived pre-signed upload/download URLs;
- acting as the same-origin application facade that calls the API Gateway weather and analytics routes.

Use API Gateway and Lambda for:

- current/forecast/historical weather adapters and the post-time weather snapshot;
- Open-Meteo response normalization and recommendation rules;
- analytics queries;
- scheduled exports and alert checks.

The browser uses Geoapify Map Tiles through Leaflet only to draw the interactive map and marker from the selected normalized coordinates. Spring remains the only Geoapify search/geocoding and normalization boundary; the browser must not use a client-side search control or raw map-tile data as the persisted location identity. The same selected coordinates must be used for map display, weather, and starring. Spring should validate/authenticate the incoming request and forward only required weather or analytics parameters; Lambda owns Open-Meteo normalization, recommendation, and Athena-query behavior. Do not implement the same business rule in both layers. Spring returns pre-signed URLs but never proxies image bytes. Normal Open-Meteo requests are not continuously copied to S3; the analytics prefix contains community/application data only. This extra backend-to-API-Gateway hop avoids browser CORS and hides internal serverless URLs, but the report should acknowledge its latency/complexity trade-off. API Gateway Lambda proxy integration is documented by AWS at [Lambda proxy integrations in API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html). Geoapify documents browser map-tile usage and supported renderers in [Geoapify Maps API](https://apidocs.geoapify.com/docs/maps/).

### 5.3 Freeze provider boundaries and location identity

The provider split is final:

1. React calls `GET /api/locations/search?q=`.
2. Spring Boot calls Geoapify for location, address, and POI search.
3. Spring validates the provider response and returns only this normalized application contract:

```json
{
  "locationID": "provider-stable-id",
  "name": "RMIT University",
  "address": "702 Nguyen Van Linh, District 7, Ho Chi Minh City",
  "latitude": 10.729,
  "longitude": 106.694
}
```

4. React uses the selected `latitude` and `longitude` for Geoapify map-tile display and sends the same coordinates in the weather request.
5. Spring persists the normalized location and sends the coordinates through API Gateway/Lambda to Open-Meteo.

Never expose raw Geoapify search results, perform browser-side search that bypasses Spring normalization, use Open-Meteo geocoding as the primary search, add a second map provider, or invent provider-specific application field names. The canonical application identifier is always `locationID`. Show the required Geoapify/OpenStreetMap/OpenMapTiles map attribution and Open-Meteo attribution in the UI/report, and verify each provider's current terms before release.

### 5.4 Freeze the description-only community-post model

The user supplies only a validated `description` and, optionally, one image. The server captures Open-Meteo weather at post creation. Display the description and captured snapshot together, but do not calculate or claim a match between them.

`CommunityPosts` has main partition key `postID` and **no main sort key**. Its attributes are:

- `postID`, `userID`, `locationID`, `locationName`, `address`, `latitude`, `longitude`;
- `description`, `imageKeys`, `createdAt`, `helpfulCount`, `notHelpfulCount`;
- `apiWeather.recordedAt`;
- `apiWeather.temperature_2m`;
- `apiWeather.relative_humidity_2m`;
- `apiWeather.apparent_temperature`;
- `apiWeather.precipitation_probability`;
- `apiWeather.precipitation`;
- `apiWeather.rain`;
- `apiWeather.is_day`;
- `apiWeather.wind_speed_10m`;
- `apiWeather.wind_direction_10m`;
- `apiWeather.weather_code`.

Create `LocationPostsIndex` with partition key `locationID` and sort key `createdAt`. Query it newest-first with `ScanIndexForward=false`; never scan the table for the normal location feed. `precipitation_probability` is a percentage, while `precipitation` and `rain` are millimetres. Do not add aliases, `forecastTime`, condition enums, observed measurements, or matching labels.

### 5.5 Use the exact access-pattern-driven DynamoDB design

| Table | Keys/indexes and required attributes | Main access pattern |
|---|---|---|
| `Users` | PK `userID`; `email`, `name`, `passwordHash`, `createdAt`; GSI `EmailIndex` with normalized `email` partition key | Register/login and check normalized email before creation |
| `StarredLocations` | PK `userID`, SK `locationID`; `name`, `address`, `latitude`, `longitude`, `starredAt`, `weatherAlertsEnabled` | List one user's stars; star/unstar; toggle alerts |
| `CommunityPosts` | PK `postID`; fields from Section 5.4; GSI `LocationPostsIndex` PK `locationID`, SK `createdAt` | Direct post access and newest-first location feed |
| `PostFeedback` | PK `postID`, SK `userID`; `feedbackType`, `createdAt` | Prevent duplicate feedback and enforce `HELPFUL`/`NOT_HELPFUL` |
| `AlertRules` | PK `alertRuleID`; `alertType`, `metric`, `operator`, `threshold`, `enabled` | Load system-defined alert rules |
| `AlertState` | PK `userID`, SK `locationID`; `lastTriggeredAlerts` map | Store an independent last-trigger timestamp for each alert type |

Feedback uses a DynamoDB transaction or equivalent conditional and atomic operations: insert/update the user's feedback and adjust counters safely. If vote changes are supported, decrement the old counter and increment the new counter in the same correct operation. If they are not supported, return a controlled duplicate/conflict response. There is no separate Locations table, WeatherSnapshot table, Alerts history table, or daily-summary table.

The alert path is `EventBridge -> alert Lambda -> alert-enabled StarredLocations -> Open-Meteo -> AlertRules evaluation -> AlertState cooldown -> SNS -> AlertState update`. The small assessment deployment may filter/scan stars for P1, but the report must identify that as a scale limitation rather than claim an undefined index.

### 5.6 Bound the private-S3 image flow

1. React requests `POST /api/uploads/presign` with permitted file metadata.
2. Spring authenticates the user, validates type/size, creates a server-owned object key, associates it with the user/post workflow, and returns a short-lived PUT URL.
3. React uploads bytes directly to the private S3 bucket.
4. Post creation stores only authorized `imageKeys`.
5. React requests `GET /api/posts/{postID}/image-url`; Spring checks access and returns a short-lived read URL.

For P0, permit zero or one image, JPEG/PNG/WebP only, with a documented limit such as 5 MB. Enable S3 Block Public Access and narrow CORS/IAM permissions. Never accept an arbitrary client-supplied key or imply Spring proxies image bytes. An SNS email subscription must be confirmed before it receives messages; document whether the lab supports one demonstration address or genuine per-user routing. See [Amazon SNS email subscription setup](https://docs.aws.amazon.com/sns/latest/dg/sns-email-notifications.html).

## 6. Minimum API contract

Freeze names and payloads before building the frontend.

### Spring Boot ECS endpoints

```text
POST   /api/auth/register
POST   /api/auth/login
GET    /api/locations/search?q=
GET    /api/stars
POST   /api/stars
DELETE /api/stars/{locationID}
PATCH  /api/stars/{locationID}/alerts
GET    /api/locations/{locationID}/posts
POST   /api/locations/{locationID}/posts
POST   /api/posts/{postID}/feedback
POST   /api/uploads/presign
GET    /api/posts/{postID}/image-url
GET    /api/weather?latitude=&longitude=&timezone=
GET    /api/weather/history?latitude=&longitude=&date=&timezone=
GET    /api/analytics/summary
GET    /actuator/health
```

`GET /api/locations/search?q=` is a Spring/Geoapify route, not an API Gateway weather route. Its response is a list of normalized objects containing exactly `locationID`, `name`, `address`, `latitude`, and `longitude`; the frontend never receives a raw Geoapify payload. `POST /api/stars` accepts that normalized location and persists the same `locationID`, `name`, `address`, `latitude`, and `longitude` fields under the authenticated `userID`.

The weather and analytics Spring endpoints are thin same-origin facades. They invoke these internal API Gateway/Lambda endpoints rather than duplicating their business logic:

```text
GET  /weather?latitude=&longitude=&timezone=
GET  /weather/history?latitude=&longitude=&date=&timezone=
GET  /analytics/summary
```

Every endpoint definition needs:

- request fields and validation;
- success response example;
- error response shape and HTTP status;
- authentication/ownership rule;
- downstream AWS service/API;
- timeout/retry behavior;
- one positive and one negative test.

## 7. Repository and evidence structure

Keep the existing Spring Boot project at the repository root to avoid a risky move. Add:

```text
weather/
|-- frontend/
|   |-- Dockerfile
|   `-- nginx.conf
|-- lambdas/
|   |-- pom.xml
|   `-- src/main/java/com/the/weather/lambda/
|       |-- WeatherHandler.java
|       |-- AnalyticsHandler.java
|       |-- ExportHandler.java
|       `-- AlertHandler.java
|-- infra/
|-- data/
|-- docs/
|   |-- architecture/
|   |-- evidence/
|   |-- rubric-traceability.md
|   |-- evidence-log.md
|   `-- test-matrix.md
|-- src/                         # existing Spring Boot backend
|-- Dockerfile
|-- .env.example
`-- README.md
```

Never commit AWS credentials, private tokens, `.env`, `node_modules`, `target`, generated test secrets, or learner-lab session credentials. Keep the browser-visible Geoapify map key in backend environment variable `GEOAPIFY_KEY`, return it only through the map config endpoint, restrict it to the required origins/referrers, and rotate it if exposed outside those intended requests.

## 8. Daily working procedure

Use this procedure every day:

1. At the start of each block, write one measurable output, not a vague activity.
2. Work in 50-minute focus periods with 10-minute breaks. Keep meal breaks between the supplied study blocks.
3. Build the smallest end-to-end slice before adding another feature.
4. Run relevant tests before every deployment.
5. After a deployed feature works, immediately capture:
   - UI screenshot;
   - CloudWatch/API log evidence;
   - AWS resource name;
   - test result;
   - report note explaining purpose and appropriateness;
   - source/reference used.
6. If blocked for 90 minutes, record the error, attempted fixes, and evidence; switch to an independent task and ask the tutor rather than losing a whole day.
7. Reserve the final 30 minutes each night for tests, a meaningful commit, evidence log, reference log, and next-day priorities.
8. Never finish a day with unrecorded credentials or a knowingly broken deployment on the main branch.

Use a board with `P0`, `P1`, and `P2` labels and columns `Backlog`, `Today`, `Blocked`, `Review`, and `Done`. An item enters Done only when code, deployed behavior, test, evidence, and documentation note are all present.

## 9. Session-by-session schedule

### Historical task disposition for 30-31 August

Do not infer completion from a date alone. Use the verified progress snapshot in Section 2 and the live checklist in Section 17; retain evidence for every completed item.

| Disposition | Earlier planned work | Action now |
|---|---|---|
| **Reschedule from 1 September** | AWS permissions; DynamoDB keys/GSIs; endpoint/DTO contracts; Spring and React builds; health endpoint; both Docker images; ECR pushes; ECS services; target groups; ALB routing; minimal Lambda; API Gateway; Spring invocation; browser-visible walking skeleton | Execute in the order shown for 1 September unless verified evidence already exists |
| **Covered by revised work, but verify** | P0/P1/P2 scope; two-ECS/one-ALB design; repository/evidence structure; rubric traceability; README/configuration | Check each artifact before relying on it; create or correct it in the matching later block if absent |
| **Optional/cut** | Early UI polish, elaborate scaffolding, infrastructure-as-code, nonessential automation, early SNS setup before core works | Do only after all P0 gates, report, and evidence are safe |
| **Superseded** | Separate map/geocoding providers including Mapbox; Open-Meteo geocoding; provider-specific saved IDs; structured community observations; match scoring; Alerts history; daily summaries; continuous weather storage; environmental analytics; the old 30-31 August gates | Do not implement or carry forward |

### Tuesday 1 September - P0-A cloud walking skeleton

#### 08:00-12:00

1. **08:00-08:45 - Verify AWS permissions.** In the assigned region, test or inspect permission for ECR, ECS/Fargate, ALB, Lambda, API Gateway, DynamoDB, S3, Glue, Athena, EventBridge, SNS, IAM, and CloudWatch. Record allowed/denied operations; send exact denials to the tutor.
2. **08:45-09:30 - Freeze DynamoDB.** Record every table, partition/sort key, `LocationPostsIndex`, attribute name, and required access pattern from Section 5.5.
3. **09:30-10:00 - Freeze contracts.** Record Section 6 routes, DTOs, normalized Geoapify output, exact `apiWeather` names, validation, authentication, and error shape.
4. **10:00-10:45 - Verify Spring build.** Run the clean production build and fix only build/configuration blockers.
5. **10:45-11:15 - Verify health.** Implement or confirm `/actuator/health`, add its test, and ensure it needs no authentication.
6. **11:15-12:00 - Verify React production build.** Create/repair the minimal React shell, relative `/api` client, and loading/error result view; run the production build.

**Dependency:** current source tree, revised planning DOCX, assignment/rubric, and active Learner Lab session.

**Exit gate:** permission matrix and frozen contracts exist; Spring build, health test, and React production build pass. Do not begin a large feature if this gate is open.

#### 13:00-18:00

1. **13:00-14:15 - Dockerize backend.** Build the Spring image, run it locally, hit `/actuator/health`, and keep secrets outside the image.
2. **14:15-15:30 - Dockerize frontend.** Use a multi-stage Node/Nginx image, verify `/`, relative `/api` configuration, and SPA fallback.
3. **15:30-16:15 - Push ECR images.** Create separate frontend/backend repositories, use immutable release tags, authenticate, and push both tested images.
4. **16:15-18:00 - Deploy ECS foundations.** Create/reuse the cluster, execution/task roles, log groups, security groups, two task definitions, and two Fargate services.

**Dependency:** both morning production builds and health paths pass.

**Exit gate:** both tagged images exist in ECR; both ECS services are running with expected CloudWatch logs. Record resource names, image digests, task revisions, and failures.

#### 20:00-00:00

1. **20:00-20:45 - Configure ALB.** Create two target groups with target type `ip`; use `/` and `/actuator/health`; set priority `/api/*` to backend and default `/*` to frontend.
2. **20:45-21:15 - Verify routing.** Make both target groups healthy and prove frontend and backend routes through the ALB.
3. **21:15-21:45 - Deploy minimal Lambda.** Add a deterministic hello/health handler, test it, deploy it, and enable logs.
4. **21:45-22:30 - Connect API Gateway.** Add the route/stage and Lambda integration with controlled success/error responses.
5. **22:30-23:15 - Connect Spring.** Make a thin backend endpoint call the deployed API Gateway route with bounded timeout/error handling.
6. **23:15-23:45 - Complete the visible skeleton.** Invoke it from React and show the Lambda result in the browser.
7. **23:45-00:00 - Prove and preserve.** Restart/redeploy once, repeat the path, capture UI/log/target evidence, commit, and update the learning/evidence log.

**Dependency:** both ECS services are deployed.

**Exit gate:** `Browser -> ALB -> React ECS -> Spring ECS -> API Gateway -> Lambda -> visible response` works without a Console/CLI runtime step.

**Catch-up rule:** if the gate is still open at 23:59, carry **only unfinished walking-skeleton work** into 2 September morning and cut P2 immediately.

### Wednesday 2 September - data foundation, authentication, and Geoapify

#### 08:00-12:00

- If the P0-A exit gate is open, finish only that skeleton first; large features remain blocked.
- Create the frozen DynamoDB tables/GSIs and least-privilege Spring task permissions.
- Implement repository adapters/test doubles and a consistent validation/error contract.
- Audit exact names: `userID`, `postID`, `locationID`, `alertRuleID`; correct casing before frontend work begins.

**Dependency:** the 1 September walking skeleton. If it is incomplete, P2 is already cut.

**Exit gate:** the full skeleton passes, and Spring can exercise the planned DynamoDB keys/access patterns. No normal location-post feed requires a scan.

#### 13:00-18:00

- Implement registration/login with BCrypt or Argon2 and short-lived stateless JWTs.
- Normalize email and enforce uniqueness; protect user-owned routes.
- Connect the authentication UI, protected routing, logout, and token-expiry handling.
- Test duplicate registration, wrong password, missing/invalid/expired token, and cross-user access.

**Dependency:** `Users` persistence, task IAM, and stable error DTOs.

**Exit gate:** two deployed users can register/login and neither can access the other's protected resources.

#### 20:00-00:00

- Implement `GET /api/locations/search?q=` in Spring.
- Call Geoapify from Spring only; validate/normalize each result to `{locationID, name, address, latitude, longitude}`.
- Never return the raw Geoapify response. Add bounded timeout and controlled handling for blank query, no result, malformed response, and upstream failure.
- Add the deployed search/select UI, provider attribution, tests, evidence, and report note.

**Dependency:** authenticated API path, server-side Geoapify configuration, and frozen normalized contract.

**Exit gate:** a deployed user can search/select a stable normalized Geoapify location, while all negative cases produce controlled responses.

### Thursday 3 September - Geoapify map, Open-Meteo, and starred locations

#### 08:00-12:00

- Pass the selected normalized coordinates to Geoapify Map Tiles through Leaflet and render the interactive marker; add attribution and controlled loading/error states.
- Implement `GET/POST /api/stars`, delete, and alert-toggle routes using exact `StarredLocations` fields.
- Persist the normalized `latitude`/`longitude` names unchanged; do not invent a second location identity or coordinate alias.
- Connect star/list/unstar/toggle UI and test with two users.

**Dependency:** deployed normalized Geoapify selection and authentication.

**Exit gate:** two users independently persist/list/delete locations, and the Geoapify map marker uses the selected coordinates. Only Spring's normalized Geoapify search response supplies persisted location identity.

#### 13:00-18:00

- Replace the hello handler with a weather Lambda adapter for current, hourly, and multi-day Open-Meteo calls.
- Validate coordinates/timezone, select only required variables, use bounded timeout, normalize response/WMO codes, and map provider failures.
- Configure API Gateway routes and the thin Spring `/api/weather` facade.
- Add deterministic recommendation rules for supported rain, UV, wind, temperature, and snow inputs.

**Dependency:** the API Gateway walking-skeleton path and selected latitude/longitude.

**Exit gate:** Spring sends the selected coordinates through API Gateway/Lambda to Open-Meteo and returns a stable normalized weather response.

#### 20:00-00:00

- Build deployed current/hourly/multi-day views and recommendation messages.
- Test blank/invalid coordinates, provider timeout/failure, WMO mapping, and controlled UI errors.
- Prove that the normalized Geoapify selection, Geoapify map marker, star persistence, and Open-Meteo request refer to the same coordinates.
- Capture UI, ECS, API Gateway, Lambda, provider-response, and attribution evidence.

**Dependency:** the afternoon weather route.

**Exit gate:** a fresh account completes `search -> select -> map -> weather -> star` in the deployed application.

### Friday 4 September - description-only community posts and feedback

#### 08:00-12:00

- Implement post creation with location fields, validated `description`, optional `imageKeys`, timestamps, and zeroed counters.
- Capture Open-Meteo weather server-side at creation and store exactly the Section 5.4 `apiWeather` fields and units.
- Implement location-feed access through `LocationPostsIndex` with pagination and newest-first ordering.

**Dependency:** deployed Open-Meteo adapter and frozen DynamoDB tables/indexes.

**Exit gate:** a text-only post stores `locationID`, description, exact server snapshot, and can be queried by location without a scan.

#### 13:00-18:00

- Implement the deployed description-only post form and feed; display description plus captured API weather without matching labels.
- Implement `HELPFUL`/`NOT_HELPFUL` using conditional/transactional writes and atomic counters.
- Decide and document whether a vote can change. If yes, adjust both old/new counters atomically; if no, return a controlled conflict.

**Dependency:** post create/list backend and authenticated location page.

**Exit gate:** a user can create/read a post and give duplicate-safe feedback while counters remain correct.

#### 20:00-00:00

- Test empty/oversized description, exact snapshot names, server-side capture, location isolation, empty feed, `LocationPostsIndex`, newest-first order, both feedback types, duplicate/concurrent feedback, and vote-change rule.
- Deploy and run the flow with two users.
- Capture request, UI, DynamoDB item/GSI, counter, and CloudWatch evidence; update report examples.

**Dependency:** afternoon UI and feedback path.

**Exit gate:** the deployed text-community flow works end to end and contains no structured observation or automatic comparison field.

### Saturday 5 September - S3 images and complete P0 user journey

#### 08:00-12:00

- Create a private image bucket, block public access, configure narrow CORS, lifecycle if appropriate, and least-privilege IAM.
- Implement `POST /api/uploads/presign` with authentication, permitted metadata, server-owned key generation, and a short-lived PUT URL.
- Enforce at most one JPG/PNG/WebP image and the documented size limit before issuing the URL.

**Dependency:** authenticated post flow and stable post ownership rules.

**Exit gate:** an authenticated user can obtain a bounded pre-signed PUT URL; invalid types/sizes and arbitrary keys are rejected.

#### 13:00-18:00

- Upload bytes directly from React to the private S3 URL; Spring must not proxy them.
- Store only an authorized `imageKeys` value with the post.
- Implement `GET /api/posts/{postID}/image-url` with ownership/access checks and a short-lived read URL.
- Add progress/error/display handling; test expired URL, missing object, invalid file, arbitrary key attempt, and no-image post.

**Dependency:** pre-sign route, bucket CORS, and task IAM.

**Exit gate:** an optional image uploads directly to private S3 and displays through a time-limited URL; public access remains blocked.

#### 20:00-00:00

- Deploy and run the whole core journey: fresh user -> Geoapify search -> Geoapify map -> Open-Meteo weather -> star -> description-only post with/without image -> feed -> feedback.
- Fix only P0 defects.
- Capture final-quality service/UI proof and draft Links, Summary, Introduction, features, provider responsibilities, and component justification.

**Dependency:** all P0-B slices.

**Exit gate:** the complete P0-B community journey works in AWS. If it does not, cut historical weather and alerts; use 6-7 September only for P0-B, required analytics, evidence, and documentation.

### Sunday 6 September - automated analytics path

#### 08:00-12:00

- If any P0-B gate is open, fix it first and retain only the smallest complete analytics query set.
- Freeze a community-data-only export schema for total posts, posts per day/location/user, active locations, feedback totals/helpful percentage, and activity over time.
- Implement a safe repeatable export Lambda: DynamoDB community/application data -> versioned/partitioned JSON or CSV in a dedicated S3 prefix.
- Test empty and repeated exports. Do not export normal Open-Meteo query history.

**Dependency:** stable `CommunityPosts` and `PostFeedback` data.

**Exit gate:** the export Lambda writes valid community data to S3 and safely handles an empty dataset/repeated run.

#### 13:00-18:00

- Configure Glue database/crawler or programmatic Athena DDL, depending on allowed services.
- Invoke the crawler from code/schedule rather than only the Console.
- Configure Athena workgroup/result location and run a compact deterministic query set against the exported community data.

**Dependency:** valid S3 analytics export and allowed Glue/Athena permissions.

**Exit gate:** Glue exposes the intended schema and Athena returns correct community metrics, including the defined empty-data behavior.

#### 20:00-00:00

- Implement analytics Lambda/API Gateway route, Athena polling/timeout, result mapping, Spring `GET /api/analytics/summary`, and dashboard cards.
- Prove that values come from application-produced S3 data.
- Document S3 layout, Glue table, queries, and flow. AWS explains the crawler/catalog relationship in [Use a crawler to add a table](https://docs.aws.amazon.com/athena/latest/ug/schema-crawlers.html).

**Dependency:** working Athena queries.

**Exit gate:** the UI displays at least three real Athena-derived community metrics and handles an empty dataset. All mandatory AWS categories now have an automated demonstrable path.

### Monday 7 September - P1 only after every P0 gate

#### 08:00-12:00

- If any P0-A/B/C gate is open, work only on P0, evidence, or report-critical defects.
- Otherwise implement historical Open-Meteo weather with validated date/coordinates and deployed UI/tests.
- Create the exact `AlertRules` and `AlertState` access paths. Store cooldowns as independent timestamps by alert type in `lastTriggeredAlerts`; do not create an Alerts history table.

**Dependency:** all P0 gates remain green.

**Exit gate:** P0 regression stays green; historical weather works if retained; alert rule/state reads and per-type timestamps are tested.

#### 13:00-18:00

- Implement `EventBridge -> alert Lambda -> alert-enabled StarredLocations -> Open-Meteo -> AlertRules -> AlertState cooldown -> SNS -> AlertState update`.
- Use fixed system rules, not user-defined thresholds. Confirm the SNS subscription and connect alert enable/disable UI.
- Test enabled/disabled, trigger/no-trigger, retry/idempotency, cooldown, and independent timestamps for different alert types.

**Dependency:** stars, Open-Meteo, rule/state models, and confirmed demonstration email.

**Exit gate:** a scheduled trigger produces the correct email and state update without manual runtime intervention.

**Hard cut at 18:00:** if that exit gate fails, stop P1 alert work and remove incomplete EventBridge/SNS claims from the final architecture. Never sacrifice P0, analytics, report, or formal testing for alerts.

#### 20:00-00:00

- Run full P0 regression and close release blockers.
- Freeze retained feature behavior, routes, schemas, and claimed services.
- Update architecture diagrams, component descriptions, evidence matrix, and report language to the actual deployed scope.

**Dependency:** final retained feature set after the 18:00 decision.

**Exit gate:** release scope is frozen; every service remaining in the main diagram is deployed, automatically invoked, and evidenced.

### Tuesday 8 September - release candidate, security, and report

#### 08:00-12:00

- Run backend/frontend/Lambda tests and production builds.
- Test a fresh account, empty account, two-user authorization, task restart, and API failure.
- Resolve P0 defects only.

**Dependency:** frozen scope and deployed retained services.

**Exit gate:** the release candidate is deployed with no open P0/release-blocking defect.

#### 13:00-18:00

- Review least-privilege IAM, secrets, CORS, private S3, file validation, JWT expiry, logs, timeouts, retry behavior, and error messages.
- Verify live URLs and environment-independent configuration.
- Re-run the regression subset for every correction.

**Dependency:** the morning release candidate.

**Exit gate:** the security checklist passes and no credential exists in source, image, report, logs, or package.

#### 20:00-00:00

- Complete report draft: Links, Summary, Introduction, Related Work, features, architecture diagrams, component purpose/appropriateness/trade-off, exact data/access patterns, API descriptions, learning evidence, references, and screenshot appendix.
- Show Geoapify search input/normalized output/persistence and map-display role, Open-Meteo weather role, private-S3 flow, and community-only analytics boundary.
- Change future tense to past/present tense and remove anything not implemented.

**Dependency:** final deployed architecture and evidence.

**Exit gate:** a complete report draft exists and every diagram arrow, field, route, and service claim matches real behavior.

### Wednesday 9 September - clean release, final report, evidence, and package

#### 08:00-12:00

- Perform a clean build using only the README instructions.
- Deploy the release candidate and run production smoke tests.
- Record final versions, resource names, URLs, test results, and commit hash.

**Dependency:** release candidate and reproducible instructions.

**Exit gate:** the clean deployment and full P0 smoke path pass.

#### 13:00-18:00

- Finish and export the solution architecture document.
- Check every rubric subsection, diagram label, caption, provider responsibility, reference, screenshot, hyperlink, exact API field, and data statement.
- Ensure all report images are copied to `doc_images`.
- Complete the rubric traceability and service-evidence matrices.

**Dependency:** final deployed behavior and captured evidence.

**Exit gate:** submission document, evidence, and `doc_images` are complete and consistent with code/AWS.

#### 20:00-00:00

- Run credential/secret and package-content checks; fix only evidence, documentation, build, or verified P0 release defects.
- Tag the release, save a backup, and assemble the ZIP.
- Extract the ZIP into a clean temporary directory and verify every link/file.
- Rehearse the submission and demo opening.
- Declare feature/document freeze at 23:59.

**Dependency:** final report, evidence matrices, verified build, and package manifest.

**Exit gate:** the exact submission candidate and live release are ready at 23:59. No new feature work is permitted on 10-12 September.

## 10. Formal testing and submission handoff

Testing also occurs during implementation. These two days are for formal system testing and defect fixing, not first-time testing.

### Thursday 10 September - functional and cloud-integration testing

#### 09:00-12:00

- Run all backend unit/controller/service tests, frontend tests/build/lint, Lambda tests, and container builds.
- Record versions, pass/fail totals, and known warnings.

#### 13:00-18:00

Run the fresh-user functional matrix:

- register, duplicate register, login failure/success, logout, expired token;
- Geoapify valid search, blank/invalid query, no result, upstream failure, normalized result, and stable `locationID` persistence;
- identical selected-coordinate handoff to the Geoapify map and to the Open-Meteo weather path;
- current/hourly/multi-day weather and historical weather if retained;
- star/unstar/list/alert toggle and cross-user ownership;
- text-only post and optional-image post, correct `locationID`, exact server-captured `apiWeather`, private-S3 URL flow, and invalid input/file;
- `LocationPostsIndex`, location isolation, pagination, and newest-first ordering without a scan;
- `HELPFUL`/`NOT_HELPFUL`, duplicate prevention, atomic counters, and vote-change correctness if supported;
- community-data export, S3 object, Glue discovery, Athena query, dashboard result, and empty dataset;
- retained alerts: enabled/disabled, trigger/no-trigger, cooldown, and independent timestamp per alert type.

#### 20:00-00:00

- Correlate UI actions with ALB/ECS, API Gateway, Lambda, DynamoDB, S3, Glue/Athena, EventBridge/SNS, and CloudWatch evidence.
- Restart an ECS task and repeat the smoke test.
- Triage defects by severity: P0 release blocker, P1 report/demo issue, P2 cosmetic.
- Fix and rerun only the affected regression set.

### Friday 11 September - security, resilience, regression, and rehearsal

#### 09:00-12:00

- Test missing/invalid/expired JWT, ID tampering, cross-user reads/writes, duplicate feedback, invalid coordinates/date, XSS-like text, oversized/wrong-type upload, arbitrary S3 key, and absent data.

#### 13:00-18:00

- Test upstream timeout/error, Lambda failure response, Athena timeout/empty results, scheduled duplicate-alert cooldown, mobile/tablet/desktop layout, and at least two browsers.
- Test Geoapify and Open-Meteo failures independently; verify a provider failure cannot silently substitute stale data from another provider.
- Review IAM, bucket public-access block, logs, account budget/session time, and live-service startup procedure.

#### 20:00-00:00

- Run complete regression after fixes.
- Perform two timed demo rehearsals, including Q&A explanations.
- Audit code, UI, diagram, report, evidence, ZIP, live URLs, and commit hash for consistency.
- Produce the final release candidate. No untested last-minute change is allowed afterward.

**Exit gate:** full regression passes, two rehearsals are complete, and no untested change remains.

### Saturday 12 September - submission only

- **08:00-09:00:** verify package name/checksum, report, source links, `doc_images`, release tag/commit, and live smoke path; make no discretionary code change.
- **09:00-10:00:** upload the final tested package to Canvas.
- **10:00-11:00:** download the submitted artifact and verify that it opens and contains every required file/link.
- **11:00-12:00:** save the receipt, submission screenshots, downloaded copy, and off-device backup.

The official deadline remains **23:59**. After the 9 September freeze, change only a verified release-blocking defect, rerun the full affected regression, and update the package/evidence consistently.

## 11. Solution architecture document outline

Use the sample documents only for structural inspiration. Sample 1 is strongest for feature-to-service interaction diagrams; Sample 2 is useful for the DynamoDB -> S3 -> Glue -> Athena flow; Sample 3 is a compact example. Do not copy their project-specific services or outdated manuals.

Recommended final structure:

1. **Links** - live URL, repository URL, dataset/API links, and release commit/tag.
2. **Summary (0.5)** - problem, objective, target users, and solution in one concise section.
3. **Introduction (1)**
   - motivation;
   - high-level behavior;
   - beneficiaries/target audiences;
   - scope and non-goals.
4. **Related Work (1)** - compare two or three weather/community products; identify the gap TheWeather addresses. Cite sources.
5. **Application Features** - concise final behavior with figure/appendix references.
6. **System Architecture (5)**
   - system context diagram;
   - deployment/component diagram;
   - complete interaction flows for weather, community image post/feedback, and analytics;
   - alert sequence if implemented;
   - labels on every arrow showing direction, protocol, endpoint/event, and returned data.
7. **System Component Descriptions (1)** - purpose, automated invocation, appropriateness, security/configuration, and alternative/trade-off for every component.
8. **Datasets, Data Structures, and APIs (1)**
   - DynamoDB keys, GSIs, and access patterns;
   - exact `CommunityPosts.apiWeather` fields and units;
   - Geoapify search input, normalized output, persisted location fields, and selected-coordinate map-display responsibility;
   - Open-Meteo coordinate input and current/forecast/historical/snapshot output;
   - community-only analytics S3 prefixes/formats and explicit exclusion of normal weather-query history;
   - API routes and example payloads;
   - Geoapify/OpenStreetMap/OpenMapTiles and Open-Meteo attribution, provider limits, and the two-external-API grading cap.
9. **Implementation and Test Evidence** - not a named rubric subsection, but useful for the demo-based assessment.
10. **Skill Development / Learning Evidence** - new tools, problems solved, and what was learned, supporting the 3-point criterion.
11. **References (0.5)** - IEEE style, consistent in-text numbering, current AWS/API documentation.
12. **Appendices** - UI screenshots, test summary, evidence matrix, and any readable supplementary diagrams.

The architecture diagram must show the whole process from each client operation, detailed component-to-component invocation, and each component's function. A page containing only AWS logos and unlabeled arrows is not enough.

The brief also requires acknowledgement in code near borrowed/adapted work and detailed IEEE references. Keep the reference log while coding rather than attempting to reconstruct it on 9 September. Follow the course's permitted-AI-use and disclosure rules.

## 12. Evidence matrix template

Maintain this table from the first deployed feature:

| Feature/UI action | Service/API | Automatic invocation path | Visible result | Test/log proof | Screenshot | Report section | Status |
|---|---|---|---|---|---|---|---|
| Search/select location | ECS backend, Geoapify | UI -> ALB -> Spring -> Geoapify Search API -> normalized result -> React -> Geoapify Map Tiles | Result and marker | search test + backend log | filename | 6.x/8.x | |
| Load forecast | ECS, API Gateway, Lambda, Open-Meteo | UI -> ALB -> backend ECS -> API Gateway -> Lambda -> API | Weather cards | Test ID + log request ID | filename | 6.x/8.x | |
| Register/star | ECS, DynamoDB | UI -> ALB -> backend ECS -> DynamoDB | Account/star list | API test + table operation log | filename | 6.x/8.x | |
| Create post/snapshot | ECS, API Gateway, Lambda, Open-Meteo, DynamoDB | UI -> Spring -> weather Lambda -> Open-Meteo -> exact `apiWeather` -> `CommunityPosts` | Description + snapshot | item + Lambda request ID | filename | 6.x/8.x | |
| Upload image | ECS, S3 | UI -> Spring pre-sign -> direct S3 PUT -> Spring stores authorized key -> signed read URL | Image in feed | upload test + S3 key | filename | 6.x/8.x | |
| View analytics | DynamoDB, S3, Glue, Athena, Lambda, API Gateway, ECS | export Lambda -> community data in S3 -> Glue -> Athena; UI -> ALB -> Spring -> API Gateway -> analytics Lambda -> Athena | Cards/chart | query ID + result | filename | 6.x/8.x | |
| Receive alert | EventBridge, Lambda, DynamoDB, Open-Meteo, SNS | schedule -> alert Lambda -> stars/rules/state + Open-Meteo -> cooldown -> SNS -> state update | Email | invocation ID + per-type state timestamp | filename | 6.x | |

For each service, be ready to answer:

- Why is it appropriate here?
- What invokes it?
- What input and output does it use?
- What happens on failure?
- What security/IAM boundary exists?
- What alternative did you reject and why?
- Where is the evidence that it is the deployed service?

## 13. Hard cutoff rules

1. **AWS permission failure discovered on 1 September:** record the exact denied action/service and contact the tutor immediately. Continue reversible local contracts/tests, but do not silently redesign the marked architecture.
2. **Walking skeleton not live by 1 September 23:59:** carry only its unfinished steps into 2 September morning and cut P2 immediately. Do not start a large feature until the skeleton works.
3. **Core user journey not live by 5 September 23:59:** stop historical weather and alerts. Stabilize auth, weather, DynamoDB, posts, S3, ECS, Lambda, and API Gateway.
4. **Analytics not live by 6 September 23:59:** reduce to one compact community-data export schema and three simple queries. Keep one complete Athena path; cut elaborate charts.
5. **Alerts not live by 7 September 18:00:** remove alerts/EventBridge/SNS from the claimed final architecture and use the time for P0, report, and evidence.
6. **Any service still manual on 8 September:** automate it or remove it from the claimed mark set and main diagram.
7. **After 9 September freeze:** only reproduce, fix, test, and document a verified defect. No feature expansion.

At every cutoff, remove work in this order: P2 first, then historical weather and alerts. Never cut the walking skeleton, core provider/location/weather flow, auth/community/S3 flow, required analytics path, testing, evidence, or report to rescue a lower-priority feature.

## 14. Demo plan

The assignment is demo-based and the brief allows about 30 minutes including Q&A. Prepare a 20-22 minute controlled walkthrough:

1. 0:00-2:00 - problem, users, objective, and why it is a cloud application.
2. 2:00-5:00 - one architecture diagram and the six required AWS categories.
3. 5:00-14:00 - fresh-user journey: register/login, search/map, weather/recommendation, star, community post/image, feedback.
4. 14:00-18:00 - analytics pipeline and UI result; scheduled alert if it is complete.
5. 18:00-21:00 - trace UI actions to API Gateway/Lambda logs, ECS, DynamoDB, S3, and Athena query evidence.
6. 21:00-23:00 - appropriateness, trade-offs, security, scalability, and limitations.
7. Remaining time - Q&A and tutor-directed checks.

Before the demo:

- start/extend the learner-lab session early;
- verify the live URL, ECS tasks, Lambda, API stages, data, analytics, and confirmed SNS email;
- use deterministic seed/demo data created through the application;
- have a second browser profile and a backup recording/screenshots, but be ready for a live demonstration;
- keep the final report and evidence matrix open;
- do not rely on a manual Console/CLI step to make a feature work.

## 15. Submission package

Build the ZIP exactly from the official brief, not from an old sample:

```text
TheWeather_ASM3.zip
|-- TheWeather_Solution_Architecture.pdf or .docx
|-- doc_images/
|   `-- every image used in the report
|-- code/
|   `-- source code (if within the specified size)
|-- code.txt                 # use a repository/share link if source is over 5 MB
|-- deploy/
|   |-- runnable JAR/package/Lambda bundles as applicable
|   `-- deployment notes
|-- data/
|   `-- seed/export/schema scripts or data, if any
`-- README.txt               # optional navigation/startup note
```

Final package checks:

- No AWS credentials, session tokens, `.env`, private key, or secret token.
- Repository and live URLs open from a signed-out/private browser where intended.
- Report hyperlinks, captions, page numbers, diagrams, and IEEE references work.
- `doc_images` contains every report image.
- `code` excludes `.git`, `target`, `node_modules`, caches, and local secrets.
- `deploy` contains runnable/deployable files or unambiguous links/instructions.
- `data` contains applicable data/scripts or a `data.txt` link for large data.
- The ZIP extracts cleanly and was tested from the extracted copy.
- The final release commit/tag matches the report and ZIP.
- Upload to Canvas before midday on 12 September, then download the submitted file and verify it.

## 16. Tutor message to send on 1 September

> Subject: COSC2980 Assignment 3 - TheWeather proposal and rubric clarification
>
> I am implementing TheWeather with separate Dockerized React/Nginx and Spring Boot services on ECS/Fargate behind one Application Load Balancer, plus Lambda, API Gateway, DynamoDB, S3, Athena/Glue, EventBridge/SNS, Geoapify, and Open-Meteo. Spring uses Geoapify for persistable location/address/POI search and normalization, while the browser uses Geoapify map tiles to display the same selected coordinates; Geoapify is claimed once. Open-Meteo provides weather, recommendations, alerts, and the server-captured post snapshot. Client operations will automatically invoke each claimed service, and I will claim marks for at most two external API types. Could you please confirm: (1) the current cloud implementation criterion is capped at 25 points despite the `35~0`/`32 to >0` text; (2) whether at least one service from every listed category is mandatory; (3) whether API Gateway satisfies Networking and Content Delivery and whether the supporting ALB is separately credited; (4) whether ECS/Fargate, ALB, Glue, and Athena are available/acceptable in our Learner Lab and region; (5) whether the two-provider responsibility split above is acceptable; (6) the current demo week/booking process; and (7) whether the Canvas late-penalty text saying 2 marks is a typo?

Do not stop work while awaiting the reply. Continue with reversible local contracts, tests, and the walking skeleton.

## 17. Final definition of done

Everything is ready only when all are true:

- [ ] One complete fresh-user journey works in the deployed application.
- [ ] Every claimed service/API is automatically invoked and has visible/log/test evidence.
- [ ] Geoapify search is normalized and persisted by `locationID`; Geoapify map tiles display the same selected coordinates; Open-Meteo owns weather/snapshot data.
- [ ] Community posts contain description plus the exact server-captured `apiWeather`, use `LocationPostsIndex`, and make no automatic observation-match claim.
- [ ] The six listed AWS categories have an implemented, documented path, subject to tutor clarification.
- [ ] No normal feature requires the AWS Console or CLI at runtime.
- [ ] Core positive, negative, authorization, file, upstream-failure, and empty-data tests pass.
- [ ] The report contains every 10-point rubric section and matches the final implementation.
- [ ] Diagrams trace client operations, detailed interactions, and component functions.
- [ ] Technology selections include rationale and honest alternatives/trade-offs.
- [ ] Learning evidence supports the 3-point skill criterion.
- [ ] Code and report sources are acknowledged in IEEE style as required.
- [ ] No unimplemented service or future feature is presented as complete.
- [ ] The release is tagged, backed up, live, and reproducible.
- [ ] The ZIP structure and credentials scan pass.
- [ ] Two timed demo rehearsals have been completed.
- [ ] Canvas submission is uploaded and downloaded/verified before the deadline.

## Immediate first action at 08:00 on 1 September

Open this file and complete, in order:

1. [x] Verify AWS service permissions and record exact denied operations. User-confirmed complete.
2. [x] Freeze DynamoDB tables, keys, attributes, and `LocationPostsIndex`. Design complete; AWS creation remains separate.
3. [ ] Freeze endpoints, normalized payloads, exact IDs, validation, and error contract. Draft exists; final consistency check remains.
4. [x] Verify the clean Spring production build.
5. [ ] Implement/verify and test the unauthenticated health endpoint. Live endpoint works; dedicated automated test remains.
6. [x] Verify the React production build and relative `/api` client.
7. [x] Build/test the backend Docker image.
8. [x] Build/test the React/Nginx Docker image and SPA fallback.
9. [x] Push both tagged images to separate ECR repositories.
10. [x] Deploy both ECS/Fargate services.
11. [x] Make both `ip` target groups healthy and verify ALB path routing.
12. [x] Deploy the minimal Lambda. Current HCMC weather handler is a verified prototype.
13. [x] Connect API Gateway to Lambda. Standalone invocation is verified.
14. [ ] Make Spring call API Gateway with controlled timeout/errors. **Current implementation step.**
15. [ ] Invoke the path from React and show the Lambda result in the browser.

Do not begin with CSS polishing or a large feature. The first proof must be the complete deployed walking skeleton. If it is not live by 23:59, continue only that work on 2 September morning and cut P2.

## 18. Revision changelog

- **Updated on 4 September:** verified progress snapshot; permission and DynamoDB design status; Geoapify now owns both normalized search/geocoding and browser map-tile display; Mapbox was removed; Geoapify and Open-Meteo are the two external API providers.
- **Updated previously:** control dates, instruction hierarchy, mark coverage, P0/P1/P2 scope, provider responsibilities, ECS/ALB flows, data models, API contract, 1-12 September schedule, tests, report/evidence guidance, cutoff rules, tutor message, and definition of done.
- **Removed/superseded:** actionable 30-31 August sessions; separate map/geocoding providers including Mapbox; Open-Meteo geocoding; provider-specific saved IDs; structured observation fields; match/scoring analytics; separate Locations/WeatherSnapshot/Alerts-history/daily-summary models; daily weather emails; continuous normal-weather storage; and environmental analytics.
- **Still confirm with tutor:** rubric 25-point formatting/category interpretation, API Gateway/ALB credit, Learner Lab service availability, two-provider responsibility split, current demo booking/week, and late-penalty wording.
