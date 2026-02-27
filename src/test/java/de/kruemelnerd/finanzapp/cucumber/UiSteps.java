package de.kruemelnerd.finanzapp.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.BalanceDailyRepository;
import de.kruemelnerd.finanzapp.repository.CsvArtifactRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UiSteps {
  private static final String USER_EMAIL = "user@example.com";
  private static final String USER_PASSWORD = "password123";

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
  private DialogAction nextDialogAction = DialogAction.ACCEPT;

  @Before("@ui")
  public void setUp() {
    balanceDailyRepository.deleteAll();
    csvArtifactRepository.deleteAll();
    transactionRepository.deleteAll();
    userRepository.deleteAll();

    User user = new User();
    user.setEmail(USER_EMAIL);
    user.setPasswordHash(passwordEncoder.encode(USER_PASSWORD));
    user.setDisplayName("User");
    user.setLanguage("EN");
    userRepository.saveAndFlush(user);

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    context = browser.newContext();
    page = context.newPage();
    nextDialogAction = DialogAction.ACCEPT;
    page.onDialog(dialog -> {
      DialogAction action = nextDialogAction;
      nextDialogAction = DialogAction.ACCEPT;
      if (action == DialogAction.CANCEL) {
        dialog.dismiss();
      } else {
        dialog.accept();
      }
    });
  }

  @After("@ui")
  public void tearDown() {
    if (context != null) {
      context.close();
    }
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  @Given("I am logged in via UI")
  public void iAmLoggedInViaUi() {
    page.navigate(baseUrl() + "/login");
    page.fill("input[name='username']", USER_EMAIL);
    page.fill("input[name='password']", USER_PASSWORD);
    page.click("button[type='submit']");
    page.waitForURL(baseUrl() + "/overview");
  }

  @When("I upload the sample CSV on overview")
  public void iUploadSampleCsv() throws IOException {
    page.navigate(baseUrl() + "/overview");
    byte[] bytes = loadSampleCsv();
    FilePayload payload = new FilePayload("sample-import.csv", "text/csv", bytes);
    page.locator("form[action='/overview/import-csv'] input[name='file']")
        .setInputFiles(payload);
    page.waitForResponse(
        response -> response.url().contains("/overview/import-csv"),
        () -> page.locator("form[action='/overview/import-csv'] button[type='submit']").click());
    page.waitForURL(baseUrl() + "/overview");
  }

  @When("I open the transactions page")
  public void iOpenTransactionsPage() {
    page.navigate(baseUrl() + "/transactions");
  }

  @When("I filter transactions with min {string} max {string}")
  public void iFilterTransactionsWithRange(String min, String max) {
    page.fill("form.filters input[name='minAmount']", min);
    page.fill("form.filters input[name='maxAmount']", max);
    page.click("form.filters button[type='submit']");
  }

  @When("I reset the transaction filters")
  public void iResetTheTransactionFilters() {
    page.click("form.filters a[href='/transactions']");
    page.waitForURL(baseUrl() + "/transactions");
  }

  @When("I open the settings page")
  public void iOpenSettingsPage() {
    page.navigate(baseUrl() + "/settings");
  }

  @Given("I confirm the next confirmation dialog")
  public void iConfirmTheNextConfirmationDialog() {
    nextDialogAction = DialogAction.ACCEPT;
  }

  @Given("I cancel the next confirmation dialog")
  public void iCancelTheNextConfirmationDialog() {
    nextDialogAction = DialogAction.CANCEL;
  }

  @When("I delete the transaction with amount {string}")
  public void iDeleteTheTransactionWithAmount(String amount) {
    boolean expectSubmit = nextDialogAction == DialogAction.ACCEPT;
    page.locator("#transactions-table tr:has-text('" + amount + "') summary.row-menu-trigger").click();
    page.locator("#transactions-table tr:has-text('" + amount + "') button:has-text('Delete')").click();
    if (expectSubmit) {
      page.waitForURL(baseUrl() + "/transactions");
    }
  }

  @When("I trigger delete all data")
  public void iTriggerDeleteAllData() {
    boolean expectSubmit = nextDialogAction == DialogAction.ACCEPT;
    page.locator("form[action='/settings/delete-all-data'] button").click();
    if (expectSubmit) {
      page.waitForURL(baseUrl() + "/overview");
    }
  }

  @When("I trigger delete account")
  public void iTriggerDeleteAccount() {
    boolean expectSubmit = nextDialogAction == DialogAction.ACCEPT;
    page.locator("form[action='/settings/delete-account'] button").click();
    if (expectSubmit) {
      page.waitForURL(baseUrl() + "/login");
    }
  }

  @When("I attempt login with {string} and {string}")
  public void iAttemptLoginWithAnd(String email, String password) {
    page.navigate(baseUrl() + "/login");
    page.fill("input[name='username']", email);
    page.fill("input[name='password']", password);
    page.click("button[type='submit']");
    page.waitForURL("**/login?error");
  }

  @Then("I should still be on {string}")
  public void iShouldStillBeOn(String path) {
    assertThat(page.url()).contains(path);
  }

  @Then("I should see text on page {string}")
  public void iShouldSeeTextOnPage(String text) {
    page.getByText(text).first().waitFor();
    assertThat(page.getByText(text).first().isVisible()).isTrue();
  }

  @Then("I should see transaction amount {string}")
  public void iShouldSeeTransactionAmount(String amount) {
    page.locator("#transactions-table").getByText(amount).waitFor();
    assertThat(page.locator("#transactions-table").getByText(amount).isVisible()).isTrue();
  }

  @Then("I should see transaction name containing {string}")
  public void iShouldSeeTransactionName(String name) {
    page.locator("#transactions-table").getByText(name).waitFor();
    assertThat(page.locator("#transactions-table").getByText(name).isVisible()).isTrue();
  }

  @Then("I should not see transaction amount {string}")
  public void iShouldNotSeeTransactionAmount(String amount) {
    page.waitForFunction(
        "args => !document.querySelector('#transactions-table')?.innerText.includes(args.amount)",
        java.util.Map.of("amount", amount));
    assertThat(page.locator("#transactions-table").getByText(amount).isVisible()).isFalse();
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private byte[] loadSampleCsv() throws IOException {
    try (InputStream stream = getClass().getResourceAsStream("/fixtures/sample-import.csv")) {
      if (stream == null) {
        throw new IOException("Missing sample-import.csv fixture");
      }
      return stream.readAllBytes();
    }
  }

  private enum DialogAction {
    ACCEPT,
    CANCEL
  }
}
