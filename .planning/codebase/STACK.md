# Technology Stack

**Analysis Date:** 2026-02-22

## Languages

**Primary:**
- Java 21 - Application code in `src/main/java/com/example/finanzapp/` and startup in `src/main/java/com/example/finanzapp/FinanzappApplication.java`.

**Secondary:**
- SQL (Flyway migrations) - Schema evolution in `src/main/resources/db/migration/`.
- HTML/CSS/JavaScript - Server-rendered UI templates in `src/main/resources/templates/` and styles in `src/main/resources/static/css/app.css`.
- Gherkin - BDD scenarios in `src/test/resources/features/`.

## Runtime

**Environment:**
- JVM (Java 21), configured via `pom.xml` (`<java.version>21</java.version>`).

**Package Manager:**
- Maven (declared by `pom.xml`; Maven Wrapper not detected because `mvnw`/`.mvn/` are absent).
- Lockfile: Not applicable for Maven in this repository (no `pom.lock`-style file detected).

## Frameworks

**Core:**
- Spring Boot 3.4.2 (`pom.xml`) - Application bootstrap, auto-configuration, web stack.
- Spring MVC + Thymeleaf (`spring-boot-starter-web`, `spring-boot-starter-thymeleaf` in `pom.xml`) - Server-rendered pages from `src/main/resources/templates/`.
- Spring Security (`spring-boot-starter-security` in `pom.xml`) - Form login and route protection configured in `src/main/java/com/example/finanzapp/common/config/SecurityConfig.java`.
- Spring Data JPA (`spring-boot-starter-data-jpa` in `pom.xml`) - Repository layer in `src/main/java/com/example/finanzapp/repository/`.

**Testing:**
- JUnit 5 via Spring Boot Starter Test (`spring-boot-starter-test` in `pom.xml`) - Unit/integration tests in `src/test/java/`.
- Cucumber 7.20.1 (`cucumber-java`, `cucumber-spring`, `cucumber-junit-platform-engine` in `pom.xml`) - BDD runner in `src/test/java/com/example/finanzapp/cucumber/CucumberTest.java`.
- Playwright 1.47.0 (`pom.xml`) - Browser E2E tests in `src/test/java/com/example/finanzapp/e2e/PlaywrightE2ETest.java`.

**Build/Dev:**
- Spring Boot Maven Plugin (`pom.xml`) - Packaging/running Spring Boot app.
- Maven Surefire Plugin (`pom.xml`) - Test execution and forked SQLite test DB injection.
- Spring Boot DevTools (`pom.xml`) - Local development hot-reload support.

## Key Dependencies

**Critical:**
- `org.springframework.boot:spring-boot-starter-web` - HTTP endpoints and MVC controllers in `src/main/java/com/example/finanzapp/*/*Controller.java`.
- `org.springframework.boot:spring-boot-starter-security` - Authentication/authorization in `src/main/java/com/example/finanzapp/common/config/SecurityConfig.java` and `src/main/java/com/example/finanzapp/auth/DatabaseUserDetailsService.java`.
- `org.springframework.boot:spring-boot-starter-data-jpa` - Persistence via repositories such as `src/main/java/com/example/finanzapp/repository/TransactionRepository.java`.
- `org.flywaydb:flyway-core` - Database migration lifecycle using SQL and Java migrations in `src/main/resources/db/migration/` and `src/main/java/db/migration/`.
- `org.xerial:sqlite-jdbc:3.46.0.0` - SQLite connectivity configured in `src/main/resources/application.properties`.

**Infrastructure:**
- `org.hibernate.orm:hibernate-community-dialects` - SQLite dialect support referenced by `spring.jpa.properties.hibernate.dialect` in `src/main/resources/application.properties`.
- `org.apache.commons:commons-csv:1.11.0` - CSV import parsing used by `src/main/java/com/example/finanzapp/importcsv/CsvParser.java`.

## Configuration

**Environment:**
- Primary runtime config is file-based in `src/main/resources/application.properties`.
- Test runtime overrides live in `src/test/resources/application.properties`.
- Locale default is configurable via property `app.locale.default`, consumed by `src/main/java/com/example/finanzapp/common/config/LocaleConfig.java`.
- Secret-bearing `.env` files are not detected in repository root.

**Build:**
- Build/dependency configuration: `pom.xml`.
- SQL migrations: `src/main/resources/db/migration/V1__create_users.sql` through `src/main/resources/db/migration/V9__alter_transactions_add_categories.sql`.
- Java migrations: `src/main/java/db/migration/V6__BackfillTransactionBookingComponents.java` and `src/main/java/db/migration/V10__NormalizeCategoryBooleanColumns.java`.

## Platform Requirements

**Development:**
- Java 21 JDK required (`pom.xml`).
- Maven CLI required (wrapper scripts not present).
- Local writable filesystem for SQLite DB files (`spring.datasource.url=jdbc:sqlite:finanzapp.db` in `src/main/resources/application.properties`).

**Production:**
- Deploy target is a JVM host/container running Spring Boot executable JAR built from `pom.xml`.
- Persistent disk required for SQLite database file path configured in `src/main/resources/application.properties`.

---

*Stack analysis: 2026-02-22*
