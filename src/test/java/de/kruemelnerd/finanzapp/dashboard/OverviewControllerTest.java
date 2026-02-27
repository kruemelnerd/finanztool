package de.kruemelnerd.finanzapp.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.importcsv.CsvImportFlashService;
import de.kruemelnerd.finanzapp.transactions.TransactionViewService;
import de.kruemelnerd.finanzapp.repository.CsvArtifactRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.util.ReflectionUtils;

@ExtendWith(MockitoExtension.class)
class OverviewControllerTest {
  @Mock
  private UserRepository userRepository;

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private CsvArtifactRepository csvArtifactRepository;

  @Mock
  private CsvImportFlashService csvImportFlashService;

  @Mock
  private TransactionViewService transactionViewService;

  @Mock
  private MessageSource messageSource;

  private OverviewController controller;

  @BeforeEach
  void setUp() {
    when(messageSource.getMessage(eq("common.userFallback"), any(), any())).thenReturn("User");
    controller = new OverviewController(
        userRepository,
        transactionRepository,
        csvArtifactRepository,
        csvImportFlashService,
        transactionViewService,
        messageSource);
  }

  @Test
  void overviewUsesDefaultsWhenUserMissing() {
    Model model = new ConcurrentModel();

    String view = controller.overview(model, null);

    assertThat(view).isEqualTo("overview");
    assertThat(model.getAttribute("displayName")).isEqualTo("User");
    assertThat(model.getAttribute("transactionCount")).isEqualTo(0L);
    assertThat(model.getAttribute("importCount")).isEqualTo(0L);
    assertThat(model.getAttribute("lastImportLabel")).isNull();
    assertThat(model.getAttribute("lastImportFile")).isNull();
  }

  @Test
  void overviewPopulatesStatsWhenUserExists() {
    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setDisplayName("Alex");
    user.setLanguage("EN");

    CsvArtifact artifact = new CsvArtifact();
    artifact.setOriginalFileName("import.csv");
    var uploadedAtField = ReflectionUtils.findField(CsvArtifact.class, "uploadedAt");
    assertThat(uploadedAtField).isNotNull();
    ReflectionUtils.makeAccessible(uploadedAtField);
    ReflectionUtils.setField(uploadedAtField, artifact, Instant.parse("2026-02-06T12:00:00Z"));

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.countByUserAndDeletedAtIsNull(eq(user))).thenReturn(12L);
    when(csvArtifactRepository.countByUserAndDeletedAtIsNull(eq(user))).thenReturn(2L);
    when(csvArtifactRepository.findTopByUserAndDeletedAtIsNullOrderByUploadedAtDesc(eq(user)))
        .thenReturn(Optional.of(artifact));

    Model model = new ConcurrentModel();
    String view = controller.overview(model, principal);

    assertThat(view).isEqualTo("overview");
    assertThat(model.getAttribute("displayName")).isEqualTo("Alex");
    assertThat(model.getAttribute("transactionCount")).isEqualTo(12L);
    assertThat(model.getAttribute("importCount")).isEqualTo(2L);
    assertThat(model.getAttribute("lastImportLabel")).isNotNull();
    assertThat((String) model.getAttribute("lastImportLabel")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
    assertThat(model.getAttribute("lastImportFile")).isEqualTo("import.csv");
  }

  @Test
  void overviewFormatsLastImportForGermanLanguage() {
    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("de-user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    User user = new User();
    user.setEmail("de-user@example.com");
    user.setPasswordHash("hashed");
    user.setDisplayName("Alex");
    user.setLanguage("DE");

    CsvArtifact artifact = new CsvArtifact();
    artifact.setOriginalFileName("import.csv");
    var uploadedAtField = ReflectionUtils.findField(CsvArtifact.class, "uploadedAt");
    assertThat(uploadedAtField).isNotNull();
    ReflectionUtils.makeAccessible(uploadedAtField);
    ReflectionUtils.setField(uploadedAtField, artifact, Instant.parse("2026-02-06T12:00:00Z"));

    when(userRepository.findByEmail("de-user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.countByUserAndDeletedAtIsNull(eq(user))).thenReturn(3L);
    when(csvArtifactRepository.countByUserAndDeletedAtIsNull(eq(user))).thenReturn(1L);
    when(csvArtifactRepository.findTopByUserAndDeletedAtIsNullOrderByUploadedAtDesc(eq(user)))
        .thenReturn(Optional.of(artifact));

    Model model = new ConcurrentModel();
    String view = controller.overview(model, principal);

    assertThat(view).isEqualTo("overview");
    assertThat((String) model.getAttribute("lastImportLabel")).matches("\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}");
  }
}
