package de.kruemelnerd.finanzapp.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.kruemelnerd.finanzapp.categories.CategoryBootstrapService;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.CategoryRepository;
import de.kruemelnerd.finanzapp.repository.RuleRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

@ExtendWith(MockitoExtension.class)
class RuleTransferServiceTest {
  @Mock
  private UserRepository userRepository;

  @Mock
  private RuleRepository ruleRepository;

  @Mock
  private CategoryRepository categoryRepository;

  @Mock
  private CategoryBootstrapService categoryBootstrapService;

  private User user;
  private UserDetails userDetails;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setEmail("user@example.com");
    userDetails = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("secret")
        .authorities("USER")
        .build();
  }

  @Test
  void exportAsJsonWrapsJacksonSerializationFailure() {
    RuleTransferService service = new RuleTransferService(
        userRepository,
        ruleRepository,
        categoryRepository,
        categoryBootstrapService,
        new FailingExportObjectMapper());

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user)).thenReturn(List.of());

    assertThatThrownBy(() -> service.exportAsJson(userDetails))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not serialize rules export")
        .hasCauseInstanceOf(TestJacksonException.class);
  }

  @Test
  void importFromJsonReturnsInvalidJsonWhenJacksonReadFails() {
    RuleTransferService service = new RuleTransferService(
        userRepository,
        ruleRepository,
        categoryRepository,
        categoryBootstrapService,
        new FailingImportObjectMapper());

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "rules.json",
        "application/json",
        "{}".getBytes(StandardCharsets.UTF_8));

    RuleTransferService.ImportResult result = service.importFromJson(userDetails, file);

    assertThat(result.status()).isEqualTo(RuleTransferService.ImportStatus.INVALID_JSON);
    assertThat(result.importedGroupCount()).isZero();
    assertThat(result.importedRuleCount()).isZero();
    assertThat(result.detail()).isNull();
  }

  private static final class FailingExportObjectMapper extends ObjectMapper {
    @Override
    public ObjectWriter writerWithDefaultPrettyPrinter() {
      throw new TestJacksonException("serialize boom");
    }
  }

  private static final class FailingImportObjectMapper extends ObjectMapper {
    @Override
    public <T> T readValue(byte[] src, Class<T> valueType) throws JacksonException {
      throw new TestJacksonException("parse boom");
    }
  }

  private static final class TestJacksonException extends JacksonException {
    private TestJacksonException(String message) {
      super(message);
    }
  }
}
