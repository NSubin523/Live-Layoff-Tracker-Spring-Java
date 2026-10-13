# Development deployment

Deployed October 13, 2026, in `us-east4`. Both services allow public Cloud Run
invocation while retaining their application-level Firebase authentication.

| Service | Development base URL |
| --- | --- |
| Spring | `https://layoff-api-dev-241672723896.us-east4.run.app` |
| Python | `https://layoff-chat-dev-241672723896.us-east4.run.app` |

PostgreSQL: Neon Free project `layoff-tracker-dev` (`purple-star-70410863`),
AWS Northern Virginia. Redis: Upstash Free `layoff-tracker-dev`, GCP `us-east4`.
Secrets are in Google Secret Manager, with access scoped to each runtime account.
No paid-account upgrade or paid database plan was activated. Cloud Run and other
Google services remain subject to free allowances and the trial's expiration;
Gemini and existing Firebase usage have their own quotas/billing.

Both services use request-based CPU allocation, zero minimum instances, and a
maximum of one instance. The first request after inactivity can take longer.

Verified: public feed and health return 200; invalid Firebase token returns 401;
empty telemetry batch returns 201; chat/history require authentication;
notification sending returns 404; PostgreSQL schema and Redis TLS/auth/PING work.
Successful chat generation and Android flows still need a logged-in app test.

## Deploy later changes

GitHub automatic deployment is configured through Cloud Build connection
`layoff-dev-github` in `us-east4`:

| Repository | Trigger | Deploys |
| --- | --- | --- |
| `NSubin523/Live-Layoff-Tracker-Spring-Java` | `layoff-api-dev-main` | Spring dev service |
| `NSubin523/Live-Layoff-Backend-Gemini-genai-client-` | `layoff-chat-dev-main` | Python dev service |

Work goes into `develop`. Pushes to `develop` do not deploy. Merge or push to
`main` to build and deploy the corresponding development service automatically.
Both triggers match exactly `^main$`; pull requests do not deploy. Production is
not configured. Builds use the Dockerfile, tag images with the Git commit SHA,
preserve runtime settings and secrets, then check feed/health over HTTPS.

Build definitions are stored inline in Cloud Build. View build status and logs in
Google Cloud Console → Cloud Build → Triggers / History, selecting `us-east4`.
The Cloud Build GitHub app can read the two selected repos and publish build
checks/statuses. Its connection token stays in Secret Manager. The temporary
Secret Manager Admin grant used during connection setup was removed.

For a manual override from the appropriate local repo:

```bash
bash scripts/deploy-dev.sh
```

The script rebuilds the current local source and preserves the existing service
configuration, secrets, and public invocation policy. If `gcloud` is not on PATH,
the CLI prepared in this chat can be selected while it remains in `/tmp`:

```bash
GCLOUD_BIN=/tmp/layoff-cloud-tools/google-cloud-sdk/bin/gcloud \
CLOUDSDK_CONFIG=/tmp/layoff-cloud-tools/config bash scripts/deploy-dev.sh
```

Temporary tools/configuration may be removed by the operating system. For regular
use, install the official Google Cloud CLI and authenticate your own account.
Manual deployment builds the current local checkout regardless of its branch;
use it only when deliberately overriding the `main` deployment workflow.

## Projects and services

- Cloud runtime project from signup: `project-66f10582-7e37-4d2a-915`.
- Existing Firebase project: `live-layoffs-tracker` (`555908354656`).
- Spring service: `layoff-api-dev`.
- Python service: `layoff-chat-dev` (separate repository).

These service names produce default HTTPS URLs containing `dev`. Use the returned
service URLs in the Android development build. Production should eventually use
separate services, secrets, and data. Reusing the existing Firebase project also
reuses its users and Firestore data; hosting a service under a dev name does not
isolate that data.

## Spring container

The Dockerfile builds Java 21 and runs as a non-root user. Source upload and Docker
ignore files exclude local secrets. Do not include service-account JSON keys in
images. Activate the `cloud` Spring profile to use the Cloud Run service identity.
Local execution retains the current Firebase file setup and port 8081. Cloud Run's
`PORT` overrides that port.

Required deployment settings:

| Variable | Value/source |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `cloud` |
| `FIREBASE_PROJECT_ID` | `live-layoffs-tracker` |
| `DB_URL` | Hosted PostgreSQL JDBC URL |
| `DB_USERNAME` | Development database user |
| `DB_PASSWORD` | Secret Manager secret |
| `REDIS_HOST` | Hosted Redis address reachable from Cloud Run |
| `REDIS_PORT` | Hosted Redis port |

Configure any provider-required Redis password/TLS using Spring's
`SPRING_DATA_REDIS_PASSWORD` secret and `SPRING_DATA_REDIS_SSL_ENABLED=true`.
Private database/Redis addresses also require Cloud Run VPC connectivity; a local
Docker hostname or localhost address cannot be used. Apply `db/telemetry.sql` once
to a fresh development PostgreSQL database before testing telemetry.

## Infrastructure setup reference

1. Authenticate the Google Cloud CLI, or use authenticated Cloud Shell. The local
   CLI was prepared in `/tmp/layoff-cloud-tools/google-cloud-sdk` for this chat.
2. Select a region and hosted PostgreSQL/Redis configuration after checking costs.
3. Enable Cloud Run, Cloud Build, Artifact Registry, and Secret Manager APIs in the
   runtime project. Source deployment builds using the repository Dockerfile.
4. Create a dedicated runtime service account for each service. Grant only the
   required Firestore permissions in `live-layoffs-tracker` and secret access in
   the runtime project. A separate runtime project does not automatically grant
   access to the Firebase project.
5. Store database and Gemini credentials in Secret Manager, using separate dev
   secrets. Do not paste their values into chat or commit them.
6. Deploy the services with zero minimum instances and a small maximum instance
   limit. These settings reduce compute usage but do not cap total billing;
   databases, Redis, builds, storage, and model calls can have separate costs.
7. Configure internet invocation for the Android client while retaining Firebase
   authentication on protected application routes. Python notification sending
   must remain disabled in the cloud dev container.

Retrieve each URL after a successful deployment:

```bash
gcloud run services describe layoff-api-dev \
  --project=project-66f10582-7e37-4d2a-915 --region=YOUR_REGION \
  --format='value(status.url)'
```

## Verify the running services

- Python: `GET /health` returns `{"status":"operational"}`.
- Python: `POST /api/v1/notifications/send` returns 404 in the dev container.
- Spring: `GET /api/v1/feed` returns the expected feed contract.
- Android: test Firebase login, favorites, telemetry, chat, and chat history against
  the hosted URLs, then repeat with the laptop closed.

Local unit tests do not establish cloud IAM, network connectivity, Firestore index
availability, or successful live Gemini calls. Verify these after deployment.
