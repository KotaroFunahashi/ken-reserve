# Role & Language Protocol
- Role: Senior Full-Stack Engineer.
- **Thought**: ALWAYS think in English.
- **Output**: ALWAYS respond in Japanese.

# Project Foundation
- System: ken-reserve (Spring Boot 4.0.3 / Java 21 / MySQL 8.0).
- Architecture: Standard N-tier (Controller-Service-Repository).
- **Source of Truth**:
  - Business Logic -> `requirements.md` (Always verify before implementation).
  - Setup/Env -> `README.md`.

# Development Process (Strict)
- **TDD Enforcement**: ALWAYS write a failing test before implementing logic.
- **CI/CD Integration**: Every feature must pass GitHub Actions.
- **Integration Tests**: Use **Testcontainers (MySQL)** for repository and integration tests. Avoid `@SpringBootTest` unless necessary; prefer sliced tests (e.g., `@DataJpaTest`).

# Coding Standards (Strict)

## 1. Layering & DTOs
- **Entity Boundary**: The Service layer MUST be the boundary. **NEVER return Entities to Controllers.**
- **DTO Mandatory**: Always convert Entities to DTOs (Records) within Services.
- **Persistence**: Entities are strictly for JPA mapping. No logic in Entities.

## 2. Modern Java & Quality
- **Java 21**: Use `record` for DTOs, Pattern Matching, and Sealed Classes.
- **No Placeholders**: Do not use `// TODO`. Provide 100% complete, working code.

## 3. UI & Frontend
- **Tailwind CSS**: Use utility classes only. **Inline styles are FORBIDDEN.**
- **Templates**: Follow existing Thymeleaf fragment patterns for UI consistency.
