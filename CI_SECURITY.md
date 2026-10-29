# CI and branch policy

Use feature branches for changes, open a PR into `develop`, then promote tested
changes from `develop` to `main` through a PR. A merge to `main` deploys the dev
Cloud Run service. `develop` never deploys. Production is not configured.

The CI workflow runs on PRs targeting `develop` or `main`, pushes to either branch,
manual dispatch, and a weekly scan. Its required status check is `CI gate`.
The gate fails when any job fails, is skipped, or is cancelled:

- Java 21 tests and Maven packaging, including all PostgreSQL integration tests
  against a disposable PostgreSQL 16 service. No live database or Firebase keys.
- Runtime Docker build and Trivy dependency/OS scan. Fixable HIGH/CRITICAL
  vulnerabilities block merging; all severities, including unfixed findings, are
  retained in the downloadable report. This is not a guarantee of zero risk.
- Gitleaks scans committed history for credentials.
- CodeQL security-extended analysis, with a SARIF gate that blocks security
  severity 7.0 or greater. Analysis failure or missing reports also blocks.

GitHub Actions are pinned to commit SHAs and default to a read-only token. CodeQL
alone gets permission to publish code-scanning results. Test and vulnerability
reports expire after seven days. No cloud runtime secrets are exposed to CI.

Dependabot proposes weekly Maven, Docker, and GitHub Actions updates into
`develop`; updates still need to pass CI and be merged explicitly.

Branch protection should require PRs, zero approving reviews, `CI gate`, an
up-to-date branch, and resolved review conversations. Apply it to admins as well,
and block force pushes and branch deletion. You can review and merge your own PR
after the checks pass; GitHub cannot count a self-approval as an approving review.

GitHub Actions CI is distinct from the Cloud Build deployment checks. Requiring a
post-merge Cloud Build deployment as a pre-merge check would create a deadlock.
Do not add those deployment statuses to the required branch checks.
