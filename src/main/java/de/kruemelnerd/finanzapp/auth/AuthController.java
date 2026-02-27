package de.kruemelnerd.finanzapp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {
  private final RegistrationService registrationService;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(RegistrationService registrationService, AuthenticationManager authenticationManager) {
    this.registrationService = registrationService;
    this.authenticationManager = authenticationManager;
  }

  @GetMapping("/")
  public String root() {
    return "redirect:/overview";
  }

  @GetMapping("/login")
  public String login(
      @RequestParam(name = "error", required = false) String error,
      Model model) {
    model.addAttribute("pageTitle", "page.login");
    model.addAttribute("loginError", error != null);
    return "login";
  }

  @GetMapping("/register")
  public String register(Model model) {
    model.addAttribute("pageTitle", "page.register");
    model.addAttribute("registrationForm", new RegistrationForm());
    return "register";
  }

  @PostMapping("/register")
  public String registerSubmit(
      @Valid @ModelAttribute("registrationForm") RegistrationForm form,
      BindingResult bindingResult,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("pageTitle", "page.register");
      return "register";
    }

    try {
      registrationService.register(form);
    } catch (IllegalArgumentException ex) {
      if ("email_in_use".equals(ex.getMessage())) {
        bindingResult.rejectValue("email", "email_in_use");
        model.addAttribute("pageTitle", "page.register");
        return "register";
      }
      throw ex;
    }
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(form.getEmail(), form.getPassword()));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    securityContextRepository.saveContext(context, request, response);
    return "redirect:/overview";
  }
}
