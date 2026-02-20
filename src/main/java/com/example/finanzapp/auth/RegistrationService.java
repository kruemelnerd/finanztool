package com.example.finanzapp.auth;

import com.example.finanzapp.categories.CategoryBootstrapService;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final CategoryBootstrapService categoryBootstrapService;

  public RegistrationService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      CategoryBootstrapService categoryBootstrapService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.categoryBootstrapService = categoryBootstrapService;
  }

  @Transactional
  public User register(RegistrationForm form) {
    if (userRepository.existsByEmail(form.getEmail())) {
      throw new IllegalArgumentException("email_in_use");
    }

    User user = new User();
    user.setEmail(form.getEmail());
    user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
    user.setDisplayName(resolveDisplayName(form));
    user.setLanguage("DE");
    User savedUser = userRepository.save(user);
    categoryBootstrapService.ensureDefaultUncategorized(savedUser);
    return savedUser;
  }

  private String resolveDisplayName(RegistrationForm form) {
    if (form.getDisplayName() != null && !form.getDisplayName().isBlank()) {
      return form.getDisplayName().trim();
    }
    String email = form.getEmail();
    if (email == null) {
      return null;
    }
    int atIndex = email.indexOf('@');
    if (atIndex > 0) {
      return email.substring(0, atIndex);
    }
    return email;
  }
}
