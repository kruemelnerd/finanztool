package com.example.finanzapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finanzapp.importcsv.CsvImportException;
import com.example.finanzapp.importcsv.CsvUploadService;
import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.CategoryAssignedBy;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.CategoryRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import com.example.finanzapp.settings.DataDeletionService;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class PagesControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private CategoryRepository categoryRepository;

  @MockBean
  private CsvUploadService csvUploadService;

  @MockBean
  private DataDeletionService dataDeletionService;

  @Test
  void loginPageLoads() throws Exception {
    mockMvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Login")));
  }

  @Test
  void registerPageLoads() throws Exception {
    mockMvc.perform(get("/register"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Create account")));
  }

  @Test
  void overviewRedirectsWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/overview"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  @Test
  void overviewLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/overview").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Current balance")))
        .andExpect(content().string(containsString("Last bookings")));
  }

  @Test
  void transactionsPageLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/transactions").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Min amount")));
  }

  @Test
  void settingsPageLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/settings").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Delete account")));
  }

  @Test
  void updateProfilePersistsDisplayName() throws Exception {
    createUser("user@example.com");

    mockMvc.perform(post("/settings/profile")
            .with(user("user@example.com"))
            .with(csrf())
            .param("displayName", "Alex"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsStatus", "success"));

    Optional<User> saved = userRepository.findByEmail("user@example.com");
    org.assertj.core.api.Assertions.assertThat(saved).isPresent();
    org.assertj.core.api.Assertions.assertThat(saved.get().getDisplayName()).isEqualTo("Alex");
  }

  @Test
  void updateLanguagePersistsLanguage() throws Exception {
    createUser("user@example.com");

    mockMvc.perform(post("/settings/language")
            .with(user("user@example.com"))
            .with(csrf())
            .param("language", "DE"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsStatus", "success"));

    Optional<User> saved = userRepository.findByEmail("user@example.com");
    org.assertj.core.api.Assertions.assertThat(saved).isPresent();
    org.assertj.core.api.Assertions.assertThat(saved.get().getLanguage()).isEqualTo("DE");
  }

  @Test
  void loginRestoresLanguageFromSettings() throws Exception {
    createUserWithPasswordAndLanguage("user@example.com", "password123", "EN");

    MvcResult result = mockMvc.perform(post("/login")
            .param("username", "user@example.com")
            .param("password", "password123")
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/overview"))
        .andReturn();

    Locale locale = (Locale) result.getRequest().getSession().getAttribute(
        SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME);
    org.assertj.core.api.Assertions.assertThat(locale).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void deleteTransactionRedirects() throws Exception {
    createUser("user@example.com");
    User owner = userRepository.findByEmail("user@example.com").orElseThrow();

    Transaction tx = new Transaction();
    tx.setUser(owner);
    tx.setBookingDateTime(LocalDateTime.of(2026, 2, 6, 12, 0));
    tx.setPartnerName("Sample");
    tx.setPurposeText("Sample");
    tx.setAmountCents(-1000L);
    tx = transactionRepository.save(tx);

    mockMvc.perform(post("/transactions/" + tx.getId() + "/delete").with(user("user@example.com")).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/transactions"))
        .andExpect(flash().attribute("transactionStatus", "success"));

    Transaction deleted = transactionRepository.findById(tx.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(deleted.getDeletedAt()).isNotNull();
  }

  @Test
  void setTransactionCategoryStoresManualAssignment() throws Exception {
    createUser("user@example.com");
    User owner = userRepository.findByEmail("user@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Sport");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    Transaction tx = new Transaction();
    tx.setUser(owner);
    tx.setBookingDateTime(LocalDateTime.of(2026, 2, 6, 12, 0));
    tx.setPartnerName("Sample");
    tx.setPurposeText("Sample");
    tx.setAmountCents(-1000L);
    tx = transactionRepository.save(tx);

    mockMvc.perform(post("/transactions/" + tx.getId() + "/set-category")
            .with(user("user@example.com"))
            .with(csrf())
            .param("categoryId", sub.getId().toString()))
        .andExpect(status().is3xxRedirection());

    Transaction updated = transactionRepository.findById(tx.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getCategory()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(updated.getCategory().getId()).isEqualTo(sub.getId());
    org.assertj.core.api.Assertions.assertThat(updated.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.MANUAL);
    org.assertj.core.api.Assertions.assertThat(updated.isCategoryLocked()).isTrue();
  }

  @Test
  void deleteAllDataRedirectsToOverview() throws Exception {
    createUser("user");
    mockMvc.perform(post("/settings/delete-all-data").with(user("user"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/overview"));
  }

  @Test
  void deleteAccountRedirectsToLogin() throws Exception {
    createUser("user");
    mockMvc.perform(post("/settings/delete-account").with(user("user"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  private void createUser(String email) {
    if (userRepository.existsByEmail(email)) {
      return;
    }
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hashed");
    userRepository.save(user);
  }

  private void createUserWithPasswordAndLanguage(String email, String rawPassword, String language) {
    userRepository.findByEmail(email).ifPresent(userRepository::delete);
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setLanguage(language);
    userRepository.save(user);
  }

  @Test
  void csvUploadRedirectsWithSuccessMessage() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "import.csv", "text/csv", "data".getBytes());

    when(csvUploadService.importForEmail(eq("user@example.com"), any(MultipartFile.class)))
        .thenReturn(new com.example.finanzapp.importcsv.CsvImportResult(2, 0, java.util.List.of()));

    mockMvc.perform(multipart("/settings/import-csv")
            .file(file)
            .with(user("user@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("csvImportStatus", "success"))
        .andExpect(flash().attribute("csvImportMessage", anyOf(
            is("2 transactions imported."),
            is("2 Buchungen importiert."))));
  }

  @Test
  void csvUploadRedirectsWithErrorMessage() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "import.csv", "text/csv", "".getBytes());

    when(csvUploadService.importForEmail(eq("user@example.com"), any(MultipartFile.class)))
        .thenThrow(new CsvImportException("CSV file is empty"));

    mockMvc.perform(multipart("/settings/import-csv")
            .file(file)
            .with(user("user@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("csvImportStatus", "error"))
        .andExpect(flash().attribute("csvImportMessage", "CSV file is empty"));
  }

  @Test
  void overviewCsvUploadRedirectsWithSuccessMessage() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "import.csv", "text/csv", "data".getBytes());

    when(csvUploadService.importForEmail(eq("user@example.com"), any(MultipartFile.class)))
        .thenReturn(new com.example.finanzapp.importcsv.CsvImportResult(1, 0, java.util.List.of()));

    mockMvc.perform(multipart("/overview/import-csv")
            .file(file)
            .with(user("user@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/overview"))
        .andExpect(flash().attribute("csvImportStatus", "success"))
        .andExpect(flash().attribute("csvImportMessage", anyOf(
            is("1 transactions imported."),
            is("1 Buchungen importiert."))));
  }
}
