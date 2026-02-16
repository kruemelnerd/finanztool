package com.example.finanzapp.cucumber;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finanzapp.domain.BalanceDaily;
import com.example.finanzapp.domain.CsvArtifact;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.BalanceDailyRepository;
import com.example.finanzapp.repository.CsvArtifactRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class WebSteps {
  private static final String SECURITY_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT";

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;
  private final CsvArtifactRepository csvArtifactRepository;
  private final BalanceDailyRepository balanceDailyRepository;
  private final PasswordEncoder passwordEncoder;
  private ResultActions lastAction;
  private MvcResult lastResult;
  private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor currentUser;
  private String registrationEmail;
  private String registrationPassword;

  @Autowired
  public WebSteps(
      MockMvc mockMvc,
      UserRepository userRepository,
      TransactionRepository transactionRepository,
      CsvArtifactRepository csvArtifactRepository,
      BalanceDailyRepository balanceDailyRepository,
      PasswordEncoder passwordEncoder) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.transactionRepository = transactionRepository;
    this.csvArtifactRepository = csvArtifactRepository;
    this.balanceDailyRepository = balanceDailyRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Before("not @ui")
  public void resetState() {
    balanceDailyRepository.deleteAll();
    csvArtifactRepository.deleteAllInBatch();
    transactionRepository.deleteAll();
    userRepository.deleteAll();
    currentUser = null;
    lastAction = null;
    lastResult = null;
    registrationEmail = null;
    registrationPassword = null;
  }

  @Given("I am not logged in")
  public void iAmNotLoggedIn() {
    currentUser = null;
  }

  @Given("I am logged in")
  public void iAmLoggedIn() {
    ensureUserExists("user");
    currentUser = user("user");
  }

  @Given("I am logged in as {string}")
  public void iAmLoggedInAs(String email) {
    ensureUserExists(email);
    currentUser = user(email);
  }

  @Given("I am on the registration page")
  public void iAmOnTheRegistrationPage() throws Exception {
    iAmNotLoggedIn();
    iRequest("/register");
  }

  @Given("an account exists for {string}")
  public void anAccountExistsFor(String email) {
    ensureUserExists(email);
  }

  @Given("I have twelve transactions for pagination")
  public void iHaveTwelveTransactionsForPagination() {
    User owner = ensureUserExists("user@example.com");
    for (int i = 1; i <= 12; i++) {
      Transaction tx = new Transaction();
      tx.setUser(owner);
      tx.setBookingDateTime(LocalDateTime.of(2026, 2, i, 12, 0));
      tx.setPartnerName(String.format("TX-%02d", i));
      tx.setPurposeText("Pagination");
      tx.setAmountCents(-1000L * i);
      transactionRepository.save(tx);
    }
  }

  @Given("I have transactions for amount filtering")
  public void iHaveTransactionsForAmountFiltering() {
    User owner = ensureUserExists("user@example.com");

    Transaction small = new Transaction();
    small.setUser(owner);
    small.setBookingDateTime(LocalDateTime.of(2026, 2, 1, 12, 0));
    small.setPartnerName("Small expense");
    small.setPurposeText("Filter");
    small.setAmountCents(-2000L);

    Transaction large = new Transaction();
    large.setUser(owner);
    large.setBookingDateTime(LocalDateTime.of(2026, 2, 2, 12, 0));
    large.setPartnerName("Large expense");
    large.setPurposeText("Filter");
    large.setAmountCents(-20000L);

    transactionRepository.saveAll(List.of(small, large));
  }

  @When("I register with email {string} and password {string}")
  public void iRegisterWithEmailAndPassword(String email, String password) {
    registrationEmail = email;
    registrationPassword = password;
  }

  @When("I submit the registration form")
  public void iSubmitTheRegistrationForm() throws Exception {
    MockHttpServletRequestBuilder builder = post("/register")
        .with(csrf())
        .param("email", registrationEmail)
        .param("password", registrationPassword)
        .param("displayName", "");
    lastAction = mockMvc.perform(builder);
    lastResult = lastAction.andReturn();
  }

  @When("I log in with email {string} and password {string}")
  public void iLogInWithEmailAndPassword(String email, String password) throws Exception {
    MockHttpServletRequestBuilder builder = post("/login")
        .with(csrf())
        .param("username", email)
        .param("password", password);
    lastAction = mockMvc.perform(builder);
    lastResult = lastAction.andReturn();
  }

  @When("I request {string}")
  public void iRequest(String path) throws Exception {
    MockHttpServletRequestBuilder builder = get(path);
    if (currentUser != null) {
      builder.with(currentUser);
    }
    lastAction = mockMvc.perform(builder);
    lastResult = lastAction.andReturn();
  }

  @When("I post to {string}")
  public void iPostTo(String path) throws Exception {
    MockHttpServletRequestBuilder builder = post(path).with(csrf());
    if ("/settings/profile".equals(path)) {
      builder.param("displayName", "Test User");
    }
    if ("/settings/language".equals(path)) {
      builder.param("language", "EN");
    }
    if (currentUser != null) {
      builder.with(currentUser);
    }
    lastAction = mockMvc.perform(builder);
    lastResult = lastAction.andReturn();
  }

  @Then("I am redirected to login")
  public void iAmRedirectedToLogin() throws Exception {
    lastAction
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  @Then("I am redirected to {string}")
  public void iAmRedirectedTo(String path) throws Exception {
    lastAction
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(path));
  }

  @Then("I see text {string}")
  public void iSeeText(String text) throws Exception {
    lastAction.andExpect(status().isOk())
        .andExpect(content().string(containsString(text)));
  }

  @Then("I do not see text {string}")
  public void iDoNotSeeText(String text) throws Exception {
    lastAction.andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString(text))));
  }

  @Then("a user account exists for {string}")
  public void aUserAccountExistsFor(String email) {
    org.assertj.core.api.Assertions.assertThat(userRepository.existsByEmail(email)).isTrue();
  }

  @Then("no user account exists for {string}")
  public void noUserAccountExistsFor(String email) {
    org.assertj.core.api.Assertions.assertThat(userRepository.existsByEmail(email)).isFalse();
  }

  @Then("I am authenticated")
  public void iAmAuthenticated() {
    Object context = lastResult.getRequest().getSession().getAttribute(SECURITY_CONTEXT_KEY);
    org.assertj.core.api.Assertions.assertThat(context).isInstanceOf(SecurityContext.class);
    SecurityContext securityContext = (SecurityContext) context;
    org.assertj.core.api.Assertions.assertThat(securityContext.getAuthentication()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(securityContext.getAuthentication().isAuthenticated()).isTrue();
  }

  @Then("I am redirected to login with an auth error")
  public void iAmRedirectedToLoginWithAnAuthError() throws Exception {
    lastAction
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error"));
  }

  @Then("I remain logged out")
  public void iRemainLoggedOut() {
    Object context = lastResult.getRequest().getSession().getAttribute(SECURITY_CONTEXT_KEY);
    if (context == null) {
      return;
    }
    SecurityContext securityContext = (SecurityContext) context;
    if (securityContext.getAuthentication() == null) {
      return;
    }
    org.assertj.core.api.Assertions.assertThat(securityContext.getAuthentication().getName())
        .isNotEqualTo("user@example.com");
  }

  private User ensureUserExists(String email) {
    return ensureUserExists(email, "password123");
  }

  private User ensureUserExists(String email, String rawPassword) {
    User existing = userRepository.findByEmail(email).orElse(null);
    if (existing != null) {
      return existing;
    }

    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setDisplayName(email);
    user.setLanguage("EN");
    return userRepository.save(user);
  }

  @Given("I have a user account with email {string} and password {string}")
  public void iHaveAUserAccountWithEmailAndPassword(String email, String password) {
    if (userRepository.existsByEmail(email)) {
      return;
    }
    ensureUserExists(email, password);
  }

  @Given("I have no account with email {string}")
  public void iHaveNoAccountWithEmail(String email) {
    userRepository.findByEmail(email).ifPresent(userRepository::delete);
  }

  @Given("I store a CSV artifact for {string}")
  public void iStoreACsvArtifactFor(String email) {
    User owner = ensureUserExists(email);
    CsvArtifact artifact = new CsvArtifact();
    artifact.setUser(owner);
    artifact.setOriginalFileName("import.csv");
    artifact.setContentType("text/csv");
    artifact.setBytes(new byte[] {1, 2, 3});
    artifact.setSizeBytes(3L);
    artifact.setDeletedAt(null);
    csvArtifactRepository.save(artifact);
  }

  @Given("I store a balance point for {string}")
  public void iStoreABalancePointFor(String email) {
    User owner = ensureUserExists(email);
    BalanceDaily point = new BalanceDaily();
    point.setUser(owner);
    point.setDate(LocalDate.now());
    point.setBalanceCentsEndOfDay(1000L);
    balanceDailyRepository.save(point);
  }

  @Given("I soft-delete all transactions for {string}")
  public void iSoftDeleteAllTransactionsFor(String email) {
    User owner = ensureUserExists(email);
    transactionRepository.softDeleteByUser(owner, Instant.now());
  }
}
