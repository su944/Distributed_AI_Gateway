# Distributed AI Gateway

## Baseline and scope
- Java 21, Spring Boot, Maven; a modular monolith organized by business capability.
- Read README.md and applicable specifications before changes. Distinguish implemented capabilities from plans.
- Phase 1 supplies bootstrap, configuration, health, tests, CI, and optional application-only Docker execution.
- Do not introduce PostgreSQL, Redis, Kafka, or Kubernetes until a feature requires them.

## Architecture and coding standards
- Root package: `com.sunny.gateway`.
- Capabilities: `gateway`, `provider`, `routing`, `authentication`, `ratelimit`, `tenant`, `usage`, `config`, `common`.
- Follow SOLID and constructor dependency injection; keep controllers thin.
- Separate business rules from HTTP, vendor, and storage infrastructure within each capability as needed.
- Use interfaces at provider integration boundaries; avoid speculative abstractions and empty placeholder classes.
- Keep routing, authentication, rate limiting, and caching separate. Enforce tenant isolation when features are introduced.
- Use Spring MVC ProblemDetail conventions for framework HTTP errors. Future domain errors must be explicitly mapped and sanitized; never expose credentials, upstream payloads, or stack traces.
- Never hardcode credentials or log prompts/secrets. `.env` is local-only; `.env.example` contains no secrets.
- Expose only minimal health information; production management access is an infrastructure responsibility until authentication exists.

## Development rules
- Explain architectural decisions and implement one feature at a time using incremental test-driven development.
- Ask before introducing new dependencies unless the user's task explicitly authorizes them.
- Use JUnit 5 and Mockito where mocks are useful. Unit tests end in `Test`; integration tests end in `IT`.
- Run `./mvnw clean verify` on JDK 21 after code changes; this includes Surefire unit tests and Failsafe integration tests.
- Verify local startup and `/actuator/health` after bootstrap/configuration changes.
- Update documentation with actual commands and accurate implementation status.
- Do not commit build output, local environment files, or credentials.
- Do not commit or push without explicit permission.
