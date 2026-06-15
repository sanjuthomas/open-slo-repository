# AGENTS.md

Guidance for AI coding agents working in **open-slo-repository**.

## Project summary

Spring Boot service for creating, editing, and versioning [OpenSLO](https://github.com/OpenSLO/OpenSLO) documents. Stores YAML definitions in MongoDB with server-side deduplication, immutable version history, and a browser UI with YAML editor.

Stack: Java **21**, Maven Wrapper (`./mvnw`), MongoDB (Docker / Testcontainers), JaCoCo (**80% minimum overall coverage**).

---

## Test coverage policy (required)

**Minimum overall coverage: 80%** on the project bundle, enforced by JaCoCo during `./mvnw verify`.

Configured in `pom.xml` as `${jacoco.minimum.coverage}` (currently **0.80**). The build fails if **any** of these bundle ratios drop below 80%:

| Metric | Enforced |
|--------|----------|
| Instructions | Yes |
| Branches | Yes |
| Lines | Yes |

Agents **must**:

1. Run `./mvnw verify` after code changes — not `./mvnw test` alone (JaCoCo `check` runs in the `verify` phase).
2. Add or update tests when new behavior would drop coverage below 80%.
3. Not lower `jacoco.minimum.coverage` or remove JaCoCo limits without explicit maintainer approval.
4. Report local coverage from `target/site/jacoco/index.html` when debugging gaps.

---

## Spring Boot version policy (required)

**Authoritative version:** `spring-boot-starter-parent` in `pom.xml` (currently **4.1.0**).

Agents **must**:

1. Keep the project on **Spring Boot 4.x**. Do not downgrade to Boot 3 or mix Boot 3 APIs/starters.
2. Change the Boot version **only** by updating `<version>` in `pom.xml`. Do not pin Spring Framework, Tomcat, or other Boot-managed artifacts separately unless Boot docs require it.
3. Use **Boot 4 modular starters** — not deprecated Boot 3 names:

   | Use (Boot 4) | Do not use (Boot 3 / deprecated) |
   |--------------|-------------------------------------|
   | `spring-boot-starter-webmvc` | `spring-boot-starter-web` |
   | `spring-boot-starter-webmvc-test` | expecting `@WebMvcTest` from `starter-test` only |
   | `spring-boot-resttestclient` + `@AutoConfigureTestRestTemplate` | `org.springframework.boot.test.web.client.TestRestTemplate` without resttestclient |

4. When bumping Boot, run `./mvnw verify` and fix test autoconfigure / starter modularization before finishing.
5. Reject or defer Dependabot **major** bumps that target Boot 3-era stacks unless explicitly requested.

**Compatible versions** (defined in `pom.xml` `<properties>` — keep in sync when upgrading):

| Property | Current | Notes |
|----------|---------|--------|
| `java.version` | 21 | Minimum for this repo |
| Parent Boot | 4.1.0 | Single source of truth |
| `testcontainers.version` | 1.20.4 | Stay on 1.x until a dedicated TC 2 migration |
| `jacoco.minimum.coverage` | 0.80 | Minimum overall bundle coverage (80%) |

---

## Testing conventions (Spring Boot 4)

- Use `@MockitoBean` / `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito` — **not** `@MockBean` / `@SpyBean`.
- `@WebMvcTest` import: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- Full-stack tests with HTTP client: `@AutoConfigureTestRestTemplate` and `org.springframework.boot.resttestclient.TestRestTemplate`.
- Integration tests: `@SpringBootTest` + Testcontainers MongoDB (`OpenSloRepositoryIntegrationTest` pattern).
- Security in slice tests: `@WithMockUser` or HTTP Basic via `TestRestTemplate` with credentials from test properties.
- Always run `./mvnw verify` before proposing dependency or test changes; **overall coverage must stay ≥ 80%**.

---

## Code conventions

- Package root: `com.openslo.repository`
- DTOs: Java `record`s with Jakarta validation (`@NotNull`, `@NotBlank`) where inputs are accepted.
- Persistence: MongoDB documents via Spring Data; OpenSLO content stored as `Map<String, Object>` inside `OpenSloDocument`.
- Errors: domain exceptions (`OpenSloValidationException`, `DuplicateOpenSloException`, `OpenSloNotFoundException`) + `GlobalExceptionHandler` → JSON `ApiError`.
- Logical document identity: `{apiVersion}/{kind}/{metadata.name}` — enforced server-side for deduplication.
- Versioning: edits create a new MongoDB document with `version + 1`; prior version set `stale: true`.
- Match existing style; avoid unrelated refactors.

---

## Commands

```bash
docker compose up -d              # MongoDB only
./mvnw spring-boot:run            # run on :9090
./mvnw verify                     # tests + JaCoCo gate
./mvnw clean package              # build JAR
```

UI: http://localhost:9090/

Default credentials are in `application.properties` (`openslo` / `openslo123`).

---

## Do not

- Commit secrets, `.env`, or credentials.
- Remove or weaken Spring Security basic auth unless explicitly requested.
- Edit unrelated files or expand scope beyond the task.
- Introduce Boot 3 starters or `@MockBean` while on Boot 4.
- Lower JaCoCo coverage thresholds without maintainer approval.

---

## References

- [README.md](README.md) — features, API, OpenSLO kinds, examples
- [examples/payment-application/](examples/payment-application/) — sample OpenSLO documents
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
