# API Flow Dashboard

Discovers every service under a Vault KV v2 engine, extracts each service's
outbound `*.endpoint` config keys, resolves them back to other known services,
live-health-checks every discovered URL, and renders the result as a call
graph. Ships as a single Spring Boot jar with the React UI baked into
`static/`.

Only calls between known, discovered services are graphed. An endpoint whose
target doesn't resolve to a discovered service (a third-party SaaS dependency,
a typo, a service outside this Vault engine) is dropped, not shown as a node —
this is a graph of your APIs calling each other, nothing else.

Click any service to see everything it calls and everything that calls it,
each with live status/latency; click any arrow for that specific call's
config key, endpoint and status.

## Run in demo mode (no Vault needed)

```
./gradlew bootRun
```

Open http://localhost:8080. Demo mode (`vault.enabled=false`, the default)
serves six canned services with a realistic mix of healthy, degraded (401),
down (503) and timed-out endpoints so the color coding is visible immediately.

## Run against real Vault

The launch script already exports `VAULT_URI`, `VAULT_NAMESPACE`,
`VAULT_ROLE_ID`, `VAULT_SECRET_ID`, `VAULT_ENABLED` — this app binds directly
to those same env vars (Spring Boot relaxed binding), no script changes
needed. Just also set:

```
VAULT_ENABLED=true
```

The full Vault path looks like `secret/data/abb/account-identity-service/prd/default`,
which breaks down as:

- `secret` — the KV v2 **mount** name (`vault.kv-mount`, default `secret`)
- `abb` — a path prefix identifying your org/BU, referred to here as the
  **trigram** (`vault.trigram`, default `abb`) — *not* a separate mount
- `account-identity-service` — the service name (auto-discovered)
- `prd/default` — environment + leaf secret name (`vault.env` / default `default`)

By default the app assumes mount `secret`, trigram `abb`, env `prd`, and
discovers service names via `LIST` on `secret/metadata/abb/`. Override any of
these via `--vault.kv-mount=`, `--vault.trigram=`, `--vault.env=` flags or
`application.yml` if your layout differs (see
`src/main/resources/application.yml`).

The AppRole token needs `read` on `secret/data/abb/*` and `list` on
`secret/metadata/abb/*` — nothing else. This is almost always **broader than
a normal per-service AppRole** (which is scoped to read only its own path), so
plan on a dedicated AppRole/policy for this dashboard rather than reusing an
existing service's credentials. The dashboard never stores or exposes the
full config, only the extracted `*.endpoint` URLs; everything else (secrets,
credentials, DB config) is discarded in memory right after extraction.

## Excluding URLs and services

Two exclusion lists under `dashboard.*` (both case-insensitive):

- `excluded-url-prefixes` (default `https://pvip`) — any endpoint URL starting
  with one of these is ignored entirely, as if it were never in the config.
- `excluded-services` (default `VAULT_L1`) — any service with one of these
  names is ignored entirely: no node, no config read, no edges in or out.

Add more via `application.yml` or `--dashboard.excluded-url-prefixes[1]=...`
/ `--dashboard.excluded-services[1]=...` flags.

## How matching works

Only keys under a configured service-call prefix (`dashboard.strip-prefixes`,
default `unibank.services.`/`unibank.service.`) are considered at all —
infrastructure/backing config like `unibank.components.s3.private.endpoint`
is never extracted, since it isn't an API-to-API call.

A key like `unibank.services.accounts.internal.endpoint` is stripped of that
prefix and the `.endpoint` suffix, leaving `accounts.internal`. Each known
service name (e.g. `accounts-service`) is normalized the same way (`-service`
suffix stripped, `-`/`_` replaced with `.`) to `accounts`. The two are matched
by longest shared dotted prefix. If nothing matches a known service, the
endpoint is dropped from the graph entirely.

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

## GraphQL endpoints

A GraphQL endpoint (`.../graphql`) answers a bare health-check GET with
400/405 even when perfectly healthy, since GraphQL only accepts POST queries.
Before probing, a trailing `/graphql` (any case, with or without a trailing
slash) is stripped and the origin underneath is checked instead — e.g.
`https://abb-bridge-service.example.com/graphql` is probed at
`https://abb-bridge-service.example.com`. This only affects the health check;
the graph still displays and links to the real configured URL.
