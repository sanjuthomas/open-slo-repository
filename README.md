# OpenSLO Repository

A Spring Boot application for creating, editing, and versioning [OpenSLO](https://github.com/OpenSLO/OpenSLO) documents. OpenSLO is a vendor-neutral YAML specification for defining Service Level Objectives (SLOs) and the related objects that surround them—services, indicators, data sources, and alerting.

This repository provides a web UI and REST API backed by MongoDB. Documents are stored with immutable version history, server-side deduplication, and validation against the OpenSLO v1 schema.

---

## Table of contents

- [Why this exists](#why-this-exists)
- [Features](#features)
- [Architecture](#architecture)
- [OpenSLO kinds and relationships](#openslo-kinds-and-relationships)
- [Quick start](#quick-start)
- [Using the UI](#using-the-ui)
- [REST API](#rest-api)
- [Versioning and deduplication](#versioning-and-deduplication)
- [Configuration](#configuration)
- [Project structure](#project-structure)
- [Build and test](#build-and-test)

---

## Why this exists

Teams adopting SLO-driven reliability need a place to author, review, and evolve SLO definitions. OpenSLO expresses those definitions as declarative YAML—similar in spirit to Kubernetes manifests—so they can be shared across tools and vendors.

OpenSLO Repository focuses on the **authoring and storage** layer:

- Write valid OpenSLO YAML in a browser
- Store documents in a document database
- Track changes over time without losing history
- Prevent accidental duplicate definitions at the server

---

## Features

| Capability | Description |
|------------|-------------|
| **Full OpenSLO v1 support** | All kinds: `SLO`, `SLI`, `Service`, `DataSource`, `AlertPolicy`, `AlertCondition`, `AlertNotificationTarget` |
| **YAML editor** | CodeMirror-based editor with per-kind starter templates |
| **Single-document workflow** | Edit and save one document at a time |
| **Server-side deduplication** | Rejects creating a second active document with the same identity |
| **Immutable versioning** | Every edit creates a new version; the previous version is marked stale |
| **Validation** | Structural validation for required fields, kinds, and kind-specific rules |
| **Basic authentication** | HTTP Basic Auth via Spring Security |
| **Session persistence** | Browser session survives page refresh (stored in `sessionStorage`) |

---

## Architecture

```
┌─────────────┐     HTTP Basic Auth      ┌──────────────────┐
│  Web UI     │ ◄──────────────────────► │  Spring Boot     │
│  (static)   │      REST /api/*         │  REST controllers│
└─────────────┘                          └────────┬─────────┘
                                                  │
                                         ┌────────▼─────────┐
                                         │  Service layer   │
                                         │  validate · dedup│
                                         │  version         │
                                         └────────┬─────────┘
                                                  │
                                         ┌────────▼─────────┐
                                         │  MongoDB         │
                                         │  openslo_documents│
                                         └──────────────────┘
```

**Stack:** Java 17 · Spring Boot 3.4 · Spring Data MongoDB · Spring Security · Jackson (JSON + YAML)

Each stored document is a BSON document containing the full OpenSLO YAML structure plus metadata (`logicalKey`, `version`, `stale`, `createdAt`, `createdBy`).

---

## OpenSLO kinds and relationships

OpenSLO objects are linked **by name** (like Kubernetes references), not by database foreign keys. Your repository stores each kind as an independent document; relationships are expressed inside the YAML.

### Kind overview

| Kind | Purpose |
|------|---------|
| **Service** | Logical grouping — the system or capability being measured |
| **SLI** | Service Level Indicator — how to query metrics (good/total, threshold, etc.) |
| **SLO** | Service Level Objective — the reliability target for a service |
| **DataSource** | Connection details for a metrics backend (Prometheus, Datadog, …) |
| **AlertPolicy** | When and how to alert on an SLO |
| **AlertCondition** | The alerting rule (e.g. burn rate threshold) |
| **AlertNotificationTarget** | Where alerts are delivered (email, Slack, webhook, …) |

### How they connect

```
DataSource ──► SLI ──► SLO ◄── Service
                         │
                         └──► AlertPolicy ──► AlertCondition
                                    │
                                    └──► AlertNotificationTarget
```

### Cardinality (from the OpenSLO spec)

| Relationship | Cardinality | Notes |
|--------------|-------------|-------|
| Service → SLO | **1 : N** | Many SLOs can reference the same `spec.service` name |
| SLI → SLO | **1 : N** | One SLI can be reused via `indicatorRef` across many SLOs |
| DataSource → SLI | **1 : N** | Shared via `metricSourceRef` |
| SLO → Objective | **1 : N** | At least one required; multiple allowed with `ratioMetric` |
| SLO → AlertPolicy | **0 : N** | Optional |
| AlertPolicy → AlertCondition | **1 : 1** | `conditions` is an array but only one entry is allowed |
| AlertPolicy → NotificationTarget | **1 : N** | At least one target required |
| AlertCondition → AlertPolicy | **1 : N** | Reusable across policies |
| NotificationTarget → AlertPolicy | **1 : N** | Reusable across policies |

An SLO must reference a **service** and either an inline **indicator** or an **indicatorRef** to a standalone SLI. SLIs in turn may reference a **DataSource** or inline metric source configuration.

**Example:** A `checkout-api` Service might have separate SLOs for availability, latency, and error rate—each with its own SLI, all sharing one Prometheus DataSource.

---

## Quick start

### Prerequisites

- Java 21+
- Maven Wrapper (`./mvnw`) or Maven 3.9+
- Docker (for MongoDB)

### Run locally

1. **Start MongoDB**

```bash
docker compose up -d
```

2. **Start the application**

```bash
./mvnw spring-boot:run
```

3. **Open the UI**

http://localhost:9090

4. **Sign in** with the default credentials (see [Configuration](#configuration)):

| Setting  | Default     |
|----------|-------------|
| Username | `openslo`   |
| Password | `openslo123`|

---

## Using the UI

1. Click **Sign in** and enter your credentials.
2. Choose a **Kind** from the dropdown and click **Load template** to start from a valid example.
3. Edit the YAML in the editor. You can change any field—the editor supports the full OpenSLO specification.
4. Click **Validate** to check structure without saving.
5. Click **Save** to persist the document.

### Document list and versions

- The sidebar lists all **active** (non-stale) documents.
- Click a document to load it for editing.
- Saving an existing document creates a **new version**; the prior version appears in **Version history** as stale.
- Click **View** on any historical version to inspect it read-only.

### Session behavior

- After sign-in, your session is stored in the browser's `sessionStorage` and survives page refresh.
- Closing the browser tab clears the session.
- Click **Sign out** to end the session manually.
- If credentials expire or are rejected, you are prompted to sign in again.

### Creating a new document

Click **New**, pick a kind, load the template, customize the `metadata.name`, and save. The server rejects the save if an active document with the same identity already exists.

---

## REST API

All `/api/*` endpoints require HTTP Basic Auth.

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/auth/me` | Returns the authenticated username |

### Documents

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/documents` | List active (non-stale) documents |
| GET | `/api/documents/{logicalKey}` | Get the active document for a logical key |
| GET | `/api/documents/{logicalKey}/versions` | Full version history (newest first) |
| GET | `/api/documents/id/{id}` | Get any version by MongoDB id |
| GET | `/api/documents/exists/{logicalKey}` | Check whether an active document exists |
| POST | `/api/documents` | Create a new document |
| PUT | `/api/documents/{logicalKey}` | Update — creates a new version |
| POST | `/api/documents/validate` | Validate without saving |
| POST | `/api/documents/parse-yaml` | Parse YAML string to JSON |
| POST | `/api/documents/to-yaml` | Serialize JSON document to YAML |

**Logical key encoding in URLs:** `/` is encoded as `~`.

Example: `openslo/v1/SLO/my-availability` → `openslo~v1~SLO~my-availability`

### Example: create a Service

```bash
curl -u openslo:openslo123 -X POST http://localhost:9090/api/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "content": {
      "apiVersion": "openslo/v1",
      "kind": "Service",
      "metadata": { "name": "checkout-api", "displayName": "Checkout API" },
      "spec": { "description": "Payment checkout service" }
    }
  }'
```

### Example: update (new version)

```bash
curl -u openslo:openslo123 -X PUT \
  http://localhost:9090/api/documents/openslo~v1~Service~checkout-api \
  -H 'Content-Type: application/json' \
  -d '{
    "content": {
      "apiVersion": "openslo/v1",
      "kind": "Service",
      "metadata": { "name": "checkout-api", "displayName": "Checkout API v2" },
      "spec": { "description": "Updated description" }
    }
  }'
```

---

## Versioning and deduplication

### Identity (logical key)

Every document is uniquely identified by:

```
{apiVersion}/{kind}/{metadata.name}
```

Example: `openslo/v1/SLO/checkout-availability`

### Deduplication

| Operation | Behavior |
|-----------|----------|
| **Create** (`POST`) | Fails with `409 Conflict` if an active document with the same logical key already exists |
| **Update** (`PUT`) | Always allowed for an existing key; creates a new version |

Renaming `metadata.name` on update produces a new logical key. If that key is already taken by another active document, the update is rejected with `409 Conflict`.

### Versioning

| Field | Meaning |
|-------|---------|
| `version` | Monotonically increasing integer per logical key (starts at 1) |
| `stale` | `false` = current active version; `true` = superseded |

On every successful update:

1. The current active version is set to `stale: true`
2. A new document is inserted with `version + 1` and `stale: false`

All versions are retained in MongoDB for audit and rollback inspection.

---

## Configuration

`src/main/resources/application.properties`:

```properties
server.port=9090
spring.data.mongodb.uri=mongodb://localhost:27017/openslo
openslo.security.username=openslo
openslo.security.password=openslo123
```

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `9090` | HTTP port |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/openslo` | MongoDB connection string |
| `openslo.security.username` | `openslo` | Basic auth username |
| `openslo.security.password` | `openslo123` | Basic auth password |

Change credentials before deploying to any shared or production environment.

---

## Project structure

```
src/main/java/com/openslo/repository/
├── OpenSloRepositoryApplication.java
├── config/           # Security, Jackson, Web MVC
├── controller/       # REST API (documents, auth)
├── dto/              # Request/response records
├── exception/        # Domain exceptions and global handler
├── model/            # MongoDB document entity
├── repository/       # Spring Data MongoDB repository
└── service/          # Validation, versioning, YAML conversion

src/main/resources/
├── application.properties
└── static/           # Web UI (HTML, CSS, JS, templates)

src/test/java/        # Unit tests
docker-compose.yml    # Local MongoDB
```

---

## Build and test

```bash
# Run tests + JaCoCo coverage gate (80% minimum)
./mvnw verify

# Build JAR
./mvnw clean package

# Run packaged JAR
java -jar target/open-slo-repository-0.1.0-SNAPSHOT.jar
```

Coverage report: `target/site/jacoco/index.html`

## Agent / contributor guide

See [AGENTS.md](AGENTS.md) for Spring Boot 4 conventions, testing policy, and coding standards.

---

## References

- [OpenSLO specification](https://github.com/OpenSLO/OpenSLO)
- [OpenSLO examples](https://github.com/OpenSLO/OpenSLO/tree/main/examples)
- [openslo.com](https://openslo.com/)
