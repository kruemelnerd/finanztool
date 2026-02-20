package com.example.finanzapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.finanzapp.categories.CategoryBootstrapService;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private CategoryBootstrapService categoryBootstrapService;

  private RegistrationService registrationService;

  @BeforeEach
  void setUp() {
    registrationService = new RegistrationService(userRepository, passwordEncoder, categoryBootstrapService);
  }

  @Test
  void registerRejectsDuplicateEmail() {
    RegistrationForm form = new RegistrationForm();
    form.setEmail("user@example.com");
    form.setPassword("password123");

    when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

    assertThatThrownBy(() -> registrationService.register(form))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email_in_use");
  }

  @Test
  void registerDerivesDisplayNameFromEmail() {
    RegistrationForm form = new RegistrationForm();
    form.setEmail("alex@example.com");
    form.setPassword("password123");
    form.setDisplayName(" ");

    when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    registrationService.register(form);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();

    assertThat(saved.getDisplayName()).isEqualTo("alex");
    assertThat(saved.getPasswordHash()).isEqualTo("hashed");
    verify(categoryBootstrapService).ensureDefaultUncategorized(saved);
  }

  @Test
  void registerKeepsProvidedDisplayNameAndSetsLanguage() {
    RegistrationForm form = new RegistrationForm();
    form.setEmail("jane@example.com");
    form.setPassword("password123");
    form.setDisplayName("  Jane Doe  ");

    when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    registrationService.register(form);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();

    assertThat(saved.getDisplayName()).isEqualTo("Jane Doe");
    assertThat(saved.getLanguage()).isEqualTo("DE");
    verify(categoryBootstrapService).ensureDefaultUncategorized(saved);
  }
}
