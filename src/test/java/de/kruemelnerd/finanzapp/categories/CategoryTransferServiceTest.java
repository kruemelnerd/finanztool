package de.kruemelnerd.finanzapp.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
class CategoryTransferServiceTest {
  @Mock
  private UserRepository userRepository;

  @Mock
  private CategoryRepository categoryRepository;

  @Mock
  private RuleRepository ruleRepository;

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
    CategoryTransferService service = new CategoryTransferService(
        userRepository,
        categoryRepository,
        ruleRepository,
        categoryBootstrapService,
        new FailingExportObjectMapper());

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user))
        .thenReturn(List.of());
    when(ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user)).thenReturn(List.of());

    assertThatThrownBy(() -> service.exportAsJson(userDetails))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not serialize categories export")
        .hasCauseInstanceOf(TestJacksonException.class);
  }

  @Test
  void importFromJsonReturnsInvalidJsonWhenJacksonReadFails() {
    CategoryTransferService service = new CategoryTransferService(
        userRepository,
        categoryRepository,
        ruleRepository,
        categoryBootstrapService,
        new FailingImportObjectMapper());

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "categories.json",
        "application/json",
        "{}".getBytes(StandardCharsets.UTF_8));

    CategoryTransferService.ImportResult result = service.importFromJson(userDetails, file);

    assertThat(result.status()).isEqualTo(CategoryTransferService.ImportStatus.INVALID_JSON);
    assertThat(result.importedParentCount()).isZero();
    assertThat(result.importedSubcategoryCount()).isZero();
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
