package com.example.finanzapp.settings;

import com.example.finanzapp.domain.User;
import com.example.finanzapp.importcsv.CsvImportException;
import com.example.finanzapp.importcsv.CsvImportResult;
import com.example.finanzapp.importcsv.CsvUploadService;
import com.example.finanzapp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SettingsController {
  private final CsvUploadService csvUploadService;
  private final DataDeletionService dataDeletionService;
  private final UserRepository userRepository;
  private final LocaleResolver localeResolver;
  private final MessageSource messageSource;

  public SettingsController(
      CsvUploadService csvUploadService,
      DataDeletionService dataDeletionService,
      UserRepository userRepository,
      LocaleResolver localeResolver,
      MessageSource messageSource) {
    this.csvUploadService = csvUploadService;
    this.dataDeletionService = dataDeletionService;
    this.userRepository = userRepository;
    this.localeResolver = localeResolver;
    this.messageSource = messageSource;
  }

  @GetMapping("/settings")
  public String settings(
      Model model,
      @AuthenticationPrincipal UserDetails userDetails) {
    model.addAttribute("pageTitle", "page.settings");
    resolveUser(userDetails).ifPresent(user -> {
      model.addAttribute("displayName", user.getDisplayName());
      model.addAttribute("selectedLanguage", user.getLanguage());
    });
    return "settings";
  }

  @PostMapping("/settings/profile")
  public String updateProfile(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam("displayName") String displayName,
      RedirectAttributes redirectAttributes) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      redirectAttributes.addFlashAttribute("settingsStatus", "error");
      redirectAttributes.addFlashAttribute("settingsMessage", msg("settings.userNotFound"));
      return "redirect:/settings";
    }

    String normalized = displayName == null ? "" : displayName.trim();
    if (normalized.length() > 80) {
      redirectAttributes.addFlashAttribute("settingsStatus", "error");
      redirectAttributes.addFlashAttribute("settingsMessage", msg("settings.displayNameTooLong"));
      return "redirect:/settings";
    }

    user.get().setDisplayName(normalized.isBlank() ? null : normalized);
    userRepository.save(user.get());
    redirectAttributes.addFlashAttribute("settingsStatus", "success");
    redirectAttributes.addFlashAttribute("settingsMessage", msg("settings.profileUpdated"));
    return "redirect:/settings";
  }

  @PostMapping("/settings/language")
  public String updateLanguage(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam("language") String language,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      redirectAttributes.addFlashAttribute("settingsStatus", "error");
      redirectAttributes.addFlashAttribute("settingsMessage", msg("settings.userNotFound"));
      return "redirect:/settings";
    }

    String normalized = normalizeLanguage(language);
    user.get().setLanguage(normalized);
    userRepository.save(user.get());
    localeResolver.setLocale(request, response, toLocale(normalized));
    redirectAttributes.addFlashAttribute("settingsStatus", "success");
    redirectAttributes.addFlashAttribute("settingsMessage", msg("settings.languageUpdated"));
    return "redirect:/settings";
  }

  @PostMapping("/settings/import-csv")
  public String importCsv(
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    String email = userDetails == null ? null : userDetails.getUsername();
    try {
      CsvImportResult result = csvUploadService.importForEmail(email, file);
      redirectAttributes.addFlashAttribute("csvImportStatus", "success");
      redirectAttributes.addFlashAttribute(
          "csvImportMessage", msg("csv.import.success", result.importedCount()));
      addDuplicateNotice(result, redirectAttributes);
    } catch (CsvImportException ex) {
      redirectAttributes.addFlashAttribute("csvImportStatus", "error");
      redirectAttributes.addFlashAttribute("csvImportMessage", ex.getMessage());
    }
    return "redirect:/settings";
  }

  @PostMapping("/settings/delete-all-data")
  public String deleteAllData(
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      redirectAttributes.addFlashAttribute("csvImportStatus", "error");
      redirectAttributes.addFlashAttribute("csvImportMessage", msg("settings.userNotFound"));
      return "redirect:/settings";
    }
    dataDeletionService.softDeleteAllData(user.get());
    return "redirect:/overview";
  }

  @PostMapping("/settings/delete-account")
  public String deleteAccount(
      @AuthenticationPrincipal UserDetails userDetails,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      redirectAttributes.addFlashAttribute("csvImportStatus", "error");
      redirectAttributes.addFlashAttribute("csvImportMessage", msg("settings.userNotFound"));
      return "redirect:/login";
    }
    dataDeletionService.hardDeleteAccount(user.get());
    new SecurityContextLogoutHandler().logout(
        request,
        response,
        SecurityContextHolder.getContext().getAuthentication());
    return "redirect:/login";
  }

  private Optional<User> resolveUser(UserDetails userDetails) {
    if (userDetails == null || userDetails.getUsername() == null) {
      return Optional.empty();
    }
    return userRepository.findByEmail(userDetails.getUsername());
  }

  private void addDuplicateNotice(CsvImportResult result, RedirectAttributes redirectAttributes) {
    if (result.duplicateCount() <= 0) {
      return;
    }
    redirectAttributes.addFlashAttribute("csvImportDuplicates", result.duplicateSamples());
    redirectAttributes.addFlashAttribute("csvImportDuplicateCount", result.duplicateCount());
  }

  private String normalizeLanguage(String language) {
    if (language == null) {
      return "DE";
    }
    String normalized = language.trim().toUpperCase(Locale.ROOT);
    if ("DE".equals(normalized)) {
      return "DE";
    }
    return "EN";
  }

  private Locale toLocale(String language) {
    if ("DE".equals(language)) {
      return Locale.GERMAN;
    }
    return Locale.ENGLISH;
  }

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
