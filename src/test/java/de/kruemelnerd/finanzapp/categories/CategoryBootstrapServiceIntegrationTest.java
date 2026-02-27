package de.kruemelnerd.finanzapp.categories;

import static org.assertj.core.api.Assertions.assertThat;

import de.kruemelnerd.finanzapp.domain.Category;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.CategoryRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CategoryBootstrapServiceIntegrationTest {
  @Autowired
  private CategoryBootstrapService categoryBootstrapService;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  void ensureDefaultUncategorizedSeedsDefaultsIdempotently() {
    User user = new User();
    user.setEmail("categories-" + UUID.randomUUID() + "@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("DE");
    user = userRepository.saveAndFlush(user);

    assertThat(categoryRepository.countByUserAndDeletedAtIsNull(user)).isZero();

    Category first = categoryBootstrapService.ensureDefaultUncategorized(user);
    long seededCount = categoryRepository.countByUserAndDeletedAtIsNull(user);

    Category second = categoryBootstrapService.ensureDefaultUncategorized(user);
    long afterSecondRunCount = categoryRepository.countByUserAndDeletedAtIsNull(user);

    assertThat(seededCount).isGreaterThan(1);
    assertThat(afterSecondRunCount).isEqualTo(seededCount);
    assertThat(first.getId()).isEqualTo(second.getId());
    assertThat(first.isDefault()).isTrue();
    assertThat(first.isSystem()).isTrue();
    assertThat(first.getParent()).isNotNull();
    assertThat(first.getParent().getName()).isEqualTo("Sonstiges");
    assertThat(first.getName()).isEqualTo("Unkategorisiert");
  }
}
