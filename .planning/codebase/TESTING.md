# Testing Patterns

**Analysis Date:** 2026-02-22

## Test Framework

**Runner:**
- JUnit 5 via `spring-boot-starter-test` in `pom.xml`.
- Config: Maven Surefire plugin in `pom.xml` (forked test JVM, ByteBuddy javaagent, SQLite datasource override for tests).

**Assertion Library:**
- AssertJ (`assertThat`, `assertThatThrownBy`) in files like `src/test/java/com/example/finanzapp/importcsv/CsvParserTest.java`.

**Run Commands:**
```bash
mvn test                                              # Run all tests
mvn -Dtest=CsvParserTest test                         # Run single test class
mvn -Dtest=CsvParserTest#parseFailsWhenHeaderMissing test  # Run single test method
```

## Test File Organization

**Location:**
- Tests are separated under `src/test/java/` with package mirroring from `src/main/java/` (for example `src/test/java/com/example/finanzapp/importcsv/CsvImportServiceTest.java`).
- BDD feature files and fixtures are in `src/test/resources/features/` and `src/test/resources/fixtures/`.

**Naming:**
- Unit tests: `*Test` (`src/test/java/com/example/finanzapp/rules/RuleEngineTest.java`).
- Integration tests: `*IntegrationTest` (`src/test/java/com/example/finanzapp/repository/TransactionRepositoryIntegrationTest.java`).
- Browser E2E tests: `*E2ETest` (`src/test/java/com/example/finanzapp/e2e/PlaywrightE2ETest.java`).

**Structure:**
```
src/test/java/com/example/finanzapp/<feature>/*Test.java
src/test/java/com/example/finanzapp/<feature>/*IntegrationTest.java
src/test/java/com/example/finanzapp/e2e/*E2ETest.java
src/test/java/com/example/finanzapp/cucumber/*.java
src/test/resources/features/*.feature
src/test/resources/fixtures/*.csv
```

## Test Structure

**Suite Organization:**
```typescript
// Pattern from `src/test/java/com/example/finanzapp/importcsv/CsvImportServiceTest.java`
@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {
  @Mock private CsvArtifactRepository csvArtifactRepository;
  @Mock private TransactionRepository transactionRepository;
  private CsvImportService csvImportService;

  @BeforeEach
  void setUp() { ... }

  @Test
  void importRejectsEmptyFile() { ... }
}
```

**Patterns:**
- Setup with `@BeforeEach` and per-test fixture data builders (`src/test/java/com/example/finanzapp/transactions/TransactionViewServiceTest.java`).
- Integration/controller tests exercise HTTP endpoints through `MockMvc` and assert status/content/redirect/flash (`src/test/java/com/example/finanzapp/web/PagesControllerTest.java`).
- Assertions are behavior-focused and include domain detail (for example duplicate samples and CSV parsing fields in `src/test/java/com/example/finanzapp/importcsv/CsvImportServiceTest.java` and `src/test/java/com/example/finanzapp/importcsv/CsvParserTest.java`).

## Mocking

**Framework:** Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `when`, `verify`) in unit tests such as `src/test/java/com/example/finanzapp/auth/RegistrationServiceTest.java`.

**Patterns:**
```typescript
// Pattern from `src/test/java/com/example/finanzapp/auth/RegistrationServiceTest.java`
when(userRepository.existsByEmail("user@example.com")).thenReturn(true);
assertThatThrownBy(() -> registrationService.register(form))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("email_in_use");

ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
verify(userRepository).save(captor.capture());
```

**What to Mock:**
- Mock repository and external collaborators in service/controller unit tests (`src/test/java/com/example/finanzapp/dashboard/OverviewControllerTest.java`, `src/test/java/com/example/finanzapp/importcsv/CsvUploadServiceTest.java`).
- Use `@MockBean` for Spring context tests when isolating one dependency in full-stack web tests (`csvUploadService` in `src/test/java/com/example/finanzapp/web/PagesControllerTest.java`).

**What NOT to Mock:**
- Do not mock repositories in repository integration tests; use real Spring context + SQLite (`src/test/java/com/example/finanzapp/repository/RepositoryIntegrationTestBase.java`).
- Do not mock browser interactions in E2E tests; use real Playwright browser with running Spring app (`src/test/java/com/example/finanzapp/e2e/PlaywrightE2ETest.java`).

## Fixtures and Factories

**Test Data:**
```typescript
// Pattern from `src/test/java/com/example/finanzapp/repository/RepositoryIntegrationTestBase.java`
protected User saveUser(String email) {
  User user = new User();
  user.setEmail(email);
  user.setPasswordHash("hashed");
  return userRepository.save(user);
}
```

**Location:**
- CSV fixtures in `src/test/resources/fixtures/` are used directly in parser/import/E2E flows (`src/test/java/com/example/finanzapp/e2e/PlaywrightE2ETest.java`).
- Reusable DB fixture helpers for repository tests are centralized in `src/test/java/com/example/finanzapp/repository/RepositoryIntegrationTestBase.java`.

## Coverage

**Requirements:** None enforced (no JaCoCo or coverage threshold plugin detected in `pom.xml`).

**View Coverage:**
```bash
Not configured in repository (no dedicated coverage command detected)
```

## Test Types

**Unit Tests:**
- Service and controller logic with Mockito, no full Spring context (`src/test/java/com/example/finanzapp/importcsv/CsvImportServiceTest.java`, `src/test/java/com/example/finanzapp/dashboard/OverviewControllerTest.java`).

**Integration Tests:**
- Spring Boot integration with real repositories/web stack and SQLite-backed datasource (`src/test/java/com/example/finanzapp/repository/TransactionRepositoryIntegrationTest.java`, `src/test/java/com/example/finanzapp/web/PartialsControllerTest.java`).

**E2E Tests:**
- Playwright browser tests with real HTTP flows in `src/test/java/com/example/finanzapp/e2e/PlaywrightE2ETest.java`.
- BDD/Cucumber tests via JUnit Platform suite `src/test/java/com/example/finanzapp/cucumber/CucumberTest.java` and feature specs like `src/test/resources/features/authentication.feature`.

## Common Patterns

**Async Testing:**
```typescript
// Pattern from `src/test/java/com/example/finanzapp/e2e/PlaywrightE2ETest.java`
page.waitForResponse(
    response -> response.url().contains("/overview/import-csv"),
    () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());
page.waitForURL(baseUrl() + "/overview");
```

**Error Testing:**
```typescript
// Pattern from `src/test/java/com/example/finanzapp/importcsv/CsvParserTest.java`
assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
    .isInstanceOf(CsvImportException.class)
    .hasMessageContaining("Invalid Buchungstag");
```

---

*Testing analysis: 2026-02-22*
