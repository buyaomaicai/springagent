# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Java 17 Spring Boot application. Production code lives under `src/main/java/com/springagent`, organized by feature: `ai` contains agent and prompt strategy code, `diagnosis` owns the REST and persistence workflow, `knowledge` integrates documentation sources, `parser` handles project inputs, and `report` builds upgrade plans. Shared configuration, exceptions, and persistence helpers belong in `common`. Runtime configuration, MyBatis XML mappers, and StringTemplate prompts are in `src/main/resources`. Tests mirror the production package tree under `src/test/java`. PostgreSQL setup is kept in `deploy/`; generated Maven output in `target/` must remain untracked.

## Build, Test, and Development Commands

- `mvn clean verify` compiles the project and runs the complete test suite.
- `mvn test` runs tests without packaging the application.
- `mvn spring-boot:run` starts the API locally. Set `DEEPSEEK_API_KEY` and database variables first.
- `docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d postgres` starts the development PostgreSQL instance. Create `deploy/.env` from `deploy/.env.example`.
- `mvn package` produces the executable JAR in `target/`.

## Coding Style & Naming Conventions

Use four-space indentation, UTF-8, and standard Java formatting. Keep packages lowercase, classes and records in PascalCase, methods and fields in camelCase, and constants in `UPPER_SNAKE_CASE`. Follow existing suffixes such as `Controller`, `Service`, `ServiceImpl`, `Mapper`, `Request`, and `Response`; service interfaces currently use the `I...Service` pattern. Prefer constructor injection via Lombok's `@RequiredArgsConstructor`. No formatter or linter is configured, so format imports and code with the IDE before committing.

## Testing Guidelines

Tests use JUnit 5 through `spring-boot-starter-test`. Name test classes `*Tests` and test methods after observable behavior, for example `streamsDoneEventAfterDiagnosis`. Add focused unit tests for parsers and service logic; use `@SpringBootTest` only when the Spring context or database wiring is required. Run `mvn test` before every pull request. No coverage threshold is currently enforced.

## Commit & Pull Request Guidelines

The repository has no commit history yet. Use short, imperative, scoped subjects such as `feat(diagnosis): persist streamed responses` or `fix(parser): reject malformed pom XML`. Keep commits focused. Pull requests should explain the behavior change, list verification commands, link relevant issues, and call out schema, API, prompt, or configuration changes. Include sample requests/responses for endpoint changes.

## Security & Configuration

Never commit API keys or local `.env` files. Keep secrets in environment variables, and update `deploy/.env.example` only with safe placeholder values when adding configuration.
