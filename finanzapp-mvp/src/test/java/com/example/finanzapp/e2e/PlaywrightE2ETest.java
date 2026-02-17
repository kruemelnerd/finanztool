package com.example.finanzapp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.BalanceDailyRepository;
import com.example.finanzapp.repository.CsvArtifactRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaywrightE2ETest {
  @LocalServerPort
  private int port;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private CsvArtifactRepository csvArtifactRepository;

  @Autowired
  private BalanceDailyRepository balanceDailyRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;

  @BeforeAll
  void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  void closeBrowser() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  @BeforeEach
  void setUp() {
    balanceDailyRepository.deleteAll();
    transactionRepository.deleteAll();
    csvArtifactRepository.deleteAll();
    userRepository.deleteAll();

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash(passwordEncoder.encode("password123"));
    user.setDisplayName("User");
    user.setLanguage("EN");
    userRepository.saveAndFlush(user);

    context = browser.newContext();
    page = context.newPage();
    page.onDialog(dialog -> dialog.accept());
  }

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private void setCurrentUserLanguage(String language) {
    User user = userRepository.findByEmail("user@example.com").orElseThrow();
    user.setLanguage(language);
    userRepository.saveAndFlush(user);
  }

  private void login() {
    page.navigate(baseUrl() + "/login");
    page.fill("input[name='username']", "user@example.com");
    page.fill("input[name='password']", "password123");
    page.click("button[type='submit']");
    page.waitForURL(baseUrl() + "/overview");
  }

  private void uploadCsvFromOverview(String fileName, String csv) {
    uploadCsvFromOverview(fileName, csv.getBytes(StandardCharsets.UTF_8));
  }

  private void uploadCsvFromOverview(String fileName, byte[] content) {
    FilePayload payload = new FilePayload(fileName, "text/csv", content);

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());
    page.waitForURL(baseUrl() + "/overview");
  }

  private void closeDuplicateModalIfVisible() {
    if (page.locator("#duplicate-modal").isVisible()) {
      page.locator("#duplicate-modal button").first().click();
    }
  }

  private byte[] readFixtureBytes(String classpathPath) {
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath)) {
      if (in == null) {
        throw new IllegalArgumentException("Fixture not found: " + classpathPath);
      }
      return in.readAllBytes();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  @Test
  void loginFlowRendersOverview() {
    login();
    page.locator("text=Current balance").first().waitFor();
    page.locator("text=Last bookings").first().waitFor();

    assertThat(page.locator("text=Current balance").first().isVisible()).isTrue();
    assertThat(page.locator("text=Last bookings").first().isVisible()).isTrue();
    assertThat(page.locator("text=Upload CSV").first().isVisible()).isTrue();
  }

  @Test
  void loginWithWrongPasswordShowsError() {
    page.navigate(baseUrl() + "/login");
    page.fill("input[name='username']", "user@example.com");
    page.fill("input[name='password']", "wrong-password");
    page.click("button[type='submit']");

    page.waitForURL("**/login?error");
    assertThat(page.locator("text=Invalid email or password.").isVisible()).isTrue();
  }

  @Test
  void unauthenticatedUserIsRedirectedToLogin() {
    page.navigate(baseUrl() + "/overview");
    assertThat(page.url()).contains("/login");
    assertThat(page.locator("text=Login").isVisible()).isTrue();
  }

  @Test
  void settingsPageShowsActions() {
    login();
    page.navigate(baseUrl() + "/settings");

    assertThat(page.locator("text=Import CSV").isVisible()).isTrue();
    assertThat(page.locator("text=Upload CSV").isVisible()).isTrue();
    assertThat(page.locator("text=Delete all data").isVisible()).isTrue();
    assertThat(page.locator("text=Delete account").isVisible()).isTrue();
  }

  @Test
  void transactionsPageShowsFilters() {
    login();
    page.navigate(baseUrl() + "/transactions");

    assertThat(page.locator("form.filters").getByText("Min amount").isVisible()).isTrue();
    page.locator("#transactions-table").getByText("No transactions to show.").waitFor();
    assertThat(page.locator("#transactions-table").getByText("No transactions to show.").isVisible())
        .isTrue();
  }

  @Test
  void transactionsPageShowsCurrentBalanceFromCsvMeta() {
    login();

    String csv = String.join("\n",
        ";;;;",
        "Umsaetze Girokonto;Zeitraum: 180 Tage;;;",
        "Neuer Kontostand;334,78 EUR;;;",
        ";;;;",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "03.02.2026;03.02.2026;Lastschrift / Belastung;Auftraggeber: Markt Buchungstext: Einkauf;-52,84",
        "02.02.2026;02.02.2026;Uebertrag / Ueberweisung;Auftraggeber: Alex Buchungstext: Uebertrag;-120",
        "01.02.2026;01.02.2026;Karte;Buchungstext: Lunch;-75,00",
        ";;;;",
        "Alter Kontostand;908,13 EUR;;;");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.navigate(baseUrl() + "/transactions");
    page.locator(".current-balance").waitFor();

    assertThat(page.locator(".current-balance").getByText("Current balance").isVisible()).isTrue();
    assertThat(page.locator(".current-balance").getByText("334.78 EUR").isVisible()).isTrue();
  }

  @Test
  void overlappingImportsKeepLatestBalanceInOverviewAndTransactions() {
    setCurrentUserLanguage("DE");
    login();

    String overlap1 = String.join("\n",
        "Neuer Kontostand;200,00 EUR",
        "Alter Kontostand;500,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.03.2025;01.03.2025;LASTSCHRIFT;Buchungstext: A;-100,00",
        "02.03.2025;02.03.2025;LASTSCHRIFT;Buchungstext: B;-200,00");

    String overlap2 = String.join("\n",
        "Neuer Kontostand;100,00 EUR",
        "Alter Kontostand;700,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "05.02.2025;05.02.2025;LASTSCHRIFT;Buchungstext: C;-300,00",
        "01.03.2025;01.03.2025;LASTSCHRIFT;Buchungstext: A;-100,00",
        "02.03.2025;02.03.2025;LASTSCHRIFT;Buchungstext: B;-200,00");

    String overlap3 = String.join("\n",
        "Neuer Kontostand;100,00 EUR",
        "Alter Kontostand;1.000,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "10.01.2025;10.01.2025;LASTSCHRIFT;Buchungstext: D;-600,00",
        "05.02.2025;05.02.2025;LASTSCHRIFT;Buchungstext: C;-300,00");

    uploadCsvFromOverview("overlap-1.csv", overlap1);
    closeDuplicateModalIfVisible();
    uploadCsvFromOverview("overlap-2.csv", overlap2);
    closeDuplicateModalIfVisible();
    uploadCsvFromOverview("overlap-3.csv", overlap3);
    closeDuplicateModalIfVisible();

    page.locator(".overview-balance-amount").waitFor();
    assertThat(page.locator(".overview-balance-amount").innerText()).contains("200,00 EUR");

    page.locator(".chart-summary strong").waitFor();
    assertThat(page.locator(".chart-summary strong").innerText()).contains("200,00 EUR");

    page.navigate(baseUrl() + "/transactions");
    page.locator(".current-balance strong").waitFor();
    assertThat(page.locator(".current-balance strong").innerText()).contains("200,00 EUR");
  }

  @Test
  void realOverlapFixturesKeepExpectedLatestBalance() {
    setCurrentUserLanguage("DE");
    login();

    uploadCsvFromOverview(
        "umsaetze_mock_overlap_1_20250811_bis_20260206.csv",
        readFixtureBytes("fixtures/umsaetze_mock_overlap_1_20250811_bis_20260206.csv"));
    closeDuplicateModalIfVisible();

    uploadCsvFromOverview(
        "umsaetze_mock_overlap_2_20250720_bis_20260115.csv",
        readFixtureBytes("fixtures/umsaetze_mock_overlap_2_20250720_bis_20260115.csv"));
    closeDuplicateModalIfVisible();

    uploadCsvFromOverview(
        "umsaetze_mock_overlap_3_20250624_bis_20251220.csv",
        readFixtureBytes("fixtures/umsaetze_mock_overlap_3_20250624_bis_20251220.csv"));
    closeDuplicateModalIfVisible();

    page.locator(".overview-balance-amount").waitFor();
    assertThat(page.locator(".overview-balance-amount").innerText()).contains("-1.982,24 EUR");

    page.locator(".chart-summary strong").waitFor();
    assertThat(page.locator(".chart-summary strong").innerText()).contains("-1.982,24 EUR");
    assertThat(page.content()).contains("Buchungen am 29.01.2026");
    assertThat(page.locator(".line-chart-tooltip-line").allTextContents())
        .anySatisfy(line -> assertThat(line).contains("993,44 EUR"));

    page.navigate(baseUrl() + "/transactions");
    page.locator(".current-balance strong").waitFor();
    assertThat(page.locator(".current-balance strong").innerText()).contains("-1.982,24 EUR");
  }

  @Test
  void csvUploadShowsTransactionInTable() {
    login();

    String csv = String.join("\n",
        "Alter Kontostand;100,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: Abo;-31,00",
        "02.02.2026;02.02.2026;UEBERWEISUNG;Buchungstext: Service;-120,00",
        "03.02.2026;03.02.2026;KARTE;Buchungstext: Lunch;-75,00");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.navigate(baseUrl() + "/transactions");
    page.locator("#transactions-table").getByText("LASTSCHRIFT").waitFor();

    assertThat(page.locator("#transactions-table").getByText("LASTSCHRIFT").isVisible())
        .isTrue();
    assertThat(page.locator("#transactions-table").getByText("-31.00 EUR").isVisible())
        .isTrue();
    assertThat(page.locator("#transactions-table").getByText("KARTE").isVisible())
        .isTrue();
    assertThat(page.locator("#transactions-table").getByText("-75.00 EUR").isVisible())
        .isTrue();

    page.fill("form.filters input[name='minAmount']", "50");
    page.fill("form.filters input[name='maxAmount']", "100");
    page.click("form.filters button[type='submit']");

    page.locator("#transactions-table").getByText("KARTE").waitFor();
    assertThat(page.locator("#transactions-table").getByText("KARTE").isVisible())
        .isTrue();
    assertThat(page.locator("#transactions-table").getByText("-75.00 EUR").isVisible())
        .isTrue();
    assertThat(page.locator("#transactions-table").getByText("UEBERWEISUNG").isVisible())
        .isFalse();
    assertThat(page.locator("#transactions-table").getByText("LASTSCHRIFT").isVisible())
        .isFalse();
  }

  @Test
  void transactionsPaginationShowsSecondPage() {
    login();

    String csv = String.join("\n",
        "Alter Kontostand;100,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: One;-1,00",
        "02.02.2026;02.02.2026;LASTSCHRIFT;Buchungstext: Two;-2,00",
        "03.02.2026;03.02.2026;LASTSCHRIFT;Buchungstext: Three;-3,00",
        "04.02.2026;04.02.2026;LASTSCHRIFT;Buchungstext: Four;-4,00",
        "05.02.2026;05.02.2026;LASTSCHRIFT;Buchungstext: Five;-5,00",
        "06.02.2026;06.02.2026;LASTSCHRIFT;Buchungstext: Six;-6,00",
        "07.02.2026;07.02.2026;LASTSCHRIFT;Buchungstext: Seven;-7,00",
        "08.02.2026;08.02.2026;LASTSCHRIFT;Buchungstext: Eight;-8,00",
        "09.02.2026;09.02.2026;LASTSCHRIFT;Buchungstext: Nine;-9,00",
        "10.02.2026;10.02.2026;LASTSCHRIFT;Buchungstext: Ten;-10,00",
        "11.02.2026;11.02.2026;LASTSCHRIFT;Buchungstext: Eleven;-11,00",
        "12.02.2026;12.02.2026;LASTSCHRIFT;Buchungstext: Twelve;-12,00");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.navigate(baseUrl() + "/transactions");
    page.locator(".pagination-label").waitFor();
    assertThat(page.locator(".pagination-label").getByText("Page 1 of 2").isVisible()).isTrue();
    assertThat(page.locator("#transactions-table").getByText("Twelve").isVisible()).isTrue();
    assertThat(page.locator("#transactions-table").getByText("One").isVisible()).isFalse();

    page.locator(".table-pagination form:has-text('Next') button").click();
    page.locator(".pagination-label").getByText("Page 2 of 2").waitFor();
    assertThat(page.locator("#transactions-table").getByText("One").isVisible()).isTrue();
  }

  @Test
  void resetFiltersRestoresDefaultView() {
    login();

    String csv = String.join("\n",
        "Alter Kontostand;100,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: Abo;-31,00",
        "02.02.2026;02.02.2026;UEBERWEISUNG;Buchungstext: Service;-120,00");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.navigate(baseUrl() + "/transactions");
    page.fill("form.filters input[name='minAmount']", "100");
    page.click("form.filters button[type='submit']");

    page.waitForFunction(
        "() => !document.querySelector('#transactions-table')?.innerText.includes('LASTSCHRIFT')");
    assertThat(page.locator("#transactions-table").getByText("UEBERWEISUNG").isVisible()).isTrue();
    assertThat(page.locator("#transactions-table").getByText("LASTSCHRIFT").isVisible()).isFalse();

    page.click("form.filters a[href='/transactions']");
    page.waitForURL(baseUrl() + "/transactions");
    page.locator("#transactions-table").getByText("LASTSCHRIFT").waitFor();

    assertThat(page.locator("#transactions-table").getByText("UEBERWEISUNG").isVisible()).isTrue();
    assertThat(page.locator("#transactions-table").getByText("LASTSCHRIFT").isVisible()).isTrue();
  }

  @Test
  void deletingTransactionHidesItFromTable() {
    login();

    String csv = String.join("\n",
        "Alter Kontostand;100,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: Abo;-31,00");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.navigate(baseUrl() + "/transactions");
    page.locator("#transactions-table").getByText("LASTSCHRIFT").waitFor();
    page.locator("#transactions-table tr:has-text('LASTSCHRIFT') button:has-text('Delete')")
        .click();

    page.waitForURL(baseUrl() + "/transactions");
    page.waitForFunction(
        "() => !document.querySelector('#transactions-table')?.innerText.includes('LASTSCHRIFT')");

    assertThat(page.locator("#transactions-table").getByText("LASTSCHRIFT").isVisible()).isFalse();
  }

  @Test
  void purposeContainsFilterFindsSubstringLikeSpac() {
    login();

    String csv = String.join("\n",
        "Alter Kontostand;100,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;KARTE;Buchungstext: Space Marine 2 Ref. DL5C28T842OQG7NU;-45,00",
        "02.02.2026;02.02.2026;KARTE;Buchungstext: Grocery Store;-12,00");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.navigate(baseUrl() + "/transactions");
    page.fill("form.filters input[name='purposeContains']", "spac");
    page.click("form.filters button[type='submit']");

    page.locator("#transactions-table").getByText("Space Marine 2").waitFor();
    assertThat(page.locator("#transactions-table").getByText("Space Marine 2").isVisible())
        .isTrue();
    assertThat(page.locator("#transactions-table").getByText("Grocery Store").isVisible()).isFalse();
  }

  @Test
  void duplicateCsvUploadShowsNotice() {
    login();

    String csv = String.join("\n",
        "Alter Kontostand;100,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: Abo;-31,00");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.locator("#duplicate-modal").waitFor();
    assertThat(page.locator("#duplicate-modal").getByText("Duplicate transactions detected").isVisible())
        .isTrue();
  }

  @Test
  void duplicateCsvUploadShowsAllDuplicatesInModal() {
    login();

    String csv = String.join("\n",
        "Alter Kontostand;100,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: Abo;-31,00",
        "02.02.2026;02.02.2026;UEBERWEISUNG;Buchungstext: Service;-120,00",
        "03.02.2026;03.02.2026;KARTE;Buchungstext: Lunch;-75,00");

    FilePayload payload = new FilePayload(
        "import.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());

    page.locator("#duplicate-modal").waitFor();

    assertThat(page.locator("#duplicate-modal").getByText("3 entries were already present and were not imported.").isVisible())
        .isTrue();
    assertThat(page.locator("#duplicate-modal").getByText("2026-02-01 - LASTSCHRIFT - -31.00 EUR").isVisible())
        .isTrue();
    assertThat(page.locator("#duplicate-modal").getByText("2026-02-02 - UEBERWEISUNG - -120.00 EUR").isVisible())
        .isTrue();
    assertThat(page.locator("#duplicate-modal").getByText("2026-02-03 - KARTE - -75.00 EUR").isVisible())
        .isTrue();
  }

  @Test
  void logoutReturnsToLogin() {
    login();
    page.click("text=Log out");
    page.waitForURL(baseUrl() + "/login");

    assertThat(page.locator("text=Login").isVisible()).isTrue();
  }
}
