# Layoff Tracker Spring backend

## Run

Requires Java 21 or newer and a local Firebase service-account file at
`src/main/resources/firebase-credentials.json` (excluded from Git).
PostgreSQL is required for telemetry. Spring optionally loads
`application-local.properties` from the project root for local Docker credentials.
That file is ignored by Git. `DB_PASSWORD` can override its password.
Optional settings: `DB_URL` and `DB_USERNAME`. The existing Python-created `telemetry`
table is reused; no Hibernate schema updates run. For a new database, apply
`db/telemetry.sql` once. Future telemetry schema changes belong to reviewed SQL in
this Java project; retained Python Alembic files are historical and must not be
used to create new telemetry migrations.

Redis is required for the existing favorites toggle's debounce operation.

```bash
./mvnw spring-boot:run
```

On this Mac, Android Studio's bundled Java runtime can be used:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./mvnw spring-boot:run
```

The server listens on port 8081. Stop an older instance before starting it.

```bash
curl http://localhost:8081/api/v1/feed
```

The feed supports guests. When Authorization is supplied, it must contain a valid
Firebase bearer ID token. It returns at most 25 records, newest first, with the
same snake_case JSON contract as the former FastAPI feed endpoint.

## Tests

```bash
./mvnw test
```

Controller tests use Spring MVC `MockMvc`; service and Firestore repository tests
use Mockito. The application-context test replaces Firebase beans with mocks.
Tests do not read or write live Firestore data.

## Ownership

- Spring: feed reads, favorites toggle/listing, telemetry batches.
- Python: chat/history, ingestion and feed writes, notifications, scheduled workers.

Android's feed provider uses the existing Java/Favorites Retrofit client on port
8081. Telemetry uses that client too. Chat continues using Python on port 8000.

## Telemetry

`POST /api/v1/telemetry/batch` accepts the existing Android request:

```json
{"events":[{"user_id":"ignored","event_name":"app_launched","event_payload":null}]}
```

Returns 201 with an empty body. Firebase-authenticated requests use the verified
UID; requests without Authorization use `Non-User`. Supplied invalid tokens return
401. Client user IDs are ignored. Empty batches are accepted. Malformed events or
names longer than the database's 100-character limit return 400. Each batch is one
transaction; an insert failure rolls back all events and returns a safe 500.

PostgreSQL integration tests use a temporary schema and delete it afterward:

```bash
TEST_DB_PASSWORD="$DB_PASSWORD" ./mvnw test
```

Optional: `TEST_DB_URL`, `TEST_DB_USERNAME`. Without `TEST_DB_PASSWORD`, the three
PostgreSQL integration tests are skipped; MVC and unit tests still run.
