package de.kruemelnerd.finanzapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.kruemelnerd.finanzapp.domain.User;
import org.junit.jupiter.api.Test;

class UserRepositoryIntegrationTest extends RepositoryIntegrationTestBase {
  @Test
  void findByEmailReturnsUser() {
    User user = saveUser("user@example.com");

    assertThat(userRepository.findByEmail("user@example.com"))
        .isPresent()
        .get()
        .extracting(User::getId)
        .isEqualTo(user.getId());
  }

  @Test
  void existsByEmailReturnsFalseWhenMissing() {
    assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
  }
}
