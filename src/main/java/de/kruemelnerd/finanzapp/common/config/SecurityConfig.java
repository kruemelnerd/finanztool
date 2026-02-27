package de.kruemelnerd.finanzapp.common.config;

import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      UserRepository userRepository,
      LocaleResolver localeResolver) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(form -> form
            .loginPage("/login")
            .successHandler((request, response, authentication) -> {
              String language = userRepository.findByEmail(authentication.getName())
                  .map(User::getLanguage)
                  .orElse("DE");
              localeResolver.setLocale(request, response, toLocale(language));
              response.sendRedirect("/overview");
            })
            .permitAll())
        .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login"));
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  private Locale toLocale(String language) {
    if ("DE".equalsIgnoreCase(language)) {
      return Locale.GERMAN;
    }
    return Locale.ENGLISH;
  }
}
