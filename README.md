# API Flow Dashboard

Discovers every service under a Vault KV v2 engine, extracts each service's
outbound `*.endpoint` config keys, resolves them back to other known services
(or marks them external), live-health-checks every discovered URL, and renders
the result as a call graph. Ships as a single Spring Boot jar with the React
UI baked into `static/`.

## Run in demo mode (no Vault needed)

```
./gradlew bootRun
```

Open http://localhost:8080. Demo mode (`vault.enabled=false`, the default)
serves six canned services with a realistic mix of healthy, degraded (401),
down (503), timed-out and DNS-failing endpoints so the color coding is
visible immediately.

## Run against real Vault

The launch script already exports `VAULT_URI`, `VAULT_NAMESPACE`,
`VAULT_ROLE_ID`, `VAULT_SECRET_ID`, `VAULT_ENABLED` — this app binds directly
to those same env vars (Spring Boot relaxed binding), no script changes
needed. Just also set:

```
VAULT_ENABLED=true
```

By default the app assumes the KV v2 mount is named `abb`, reads
`<service>/prd/default` under it, and discovers service names via `LIST` on
`abb/metadata/`. Override via `--dashboard.*`/`--vault.*` flags or
`application.yml` if your mount name, environment segment, or key
prefixes/suffixes differ (see `src/main/resources/application.yml`).

The AppRole token only needs `read` on `abb/data/*` and `list` on
`abb/metadata/*` — nothing else. The dashboard never stores or exposes the
full config, only the extracted `*.endpoint` URLs; everything else (secrets,
credentials, DB config) is discarded in memory right after extraction.

## How matching works

A key like `unibank.services.accounts.internal.endpoint` is stripped of a
configurable prefix (`unibank.services.`) and the `.endpoint` suffix, leaving
`accounts.internal`. Each known service name (e.g. `accounts-service`) is
normalized the same way (`-service` suffix stripped, `-`/`_` replaced with
`.`) to `accounts`. The two are matched by longest shared dotted prefix. If
nothing matches, the target is rendered as an external/unresolved node
(dashed border) instead of being dropped — useful for spotting calls to
third-party SaaS dependencies that aren't part of your Vault-managed fleet.

## Building

```
./gradlew bootJar
```

Produces `build/libs/service.jar`. The `com.github.node-gradle.node` plugin
installs its own Node/npm and runs `npm run build`, copying `frontend/dist`
into the jar's `static/` resources before it's assembled — one command, one
jar. Skip the frontend build with `-PskipFrontendBuild` when iterating on
Java only (reuses whatever's already in `build/resources/main/static`).

## Tuning

All in `application.yml` under `dashboard.*`:

- `config-refresh-interval` (default 60s) — how often service configs are re-read from Vault
- `health-check-interval` (default 20s) — how often endpoints are re-probed
- `health-check-timeout` (default 3s)
- `trust-all-certs-for-health-checks` (default true) — health checks are read-only reachability probes; internal PRD certs are often self-signed
