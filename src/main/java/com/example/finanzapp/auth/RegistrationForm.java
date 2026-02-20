package com.example.finanzapp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {
  @NotBlank(message = "{auth.emailRequired}")
  @Email(message = "{auth.emailInvalid}")
  private String email;

  @NotBlank(message = "{auth.passwordRequired}")
  @Size(min = 8, message = "{auth.passwordRequirements}")
  private String password;

  private String displayName;

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }
}
