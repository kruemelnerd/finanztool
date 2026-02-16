package com.example.finanzapp.dashboard;

import com.example.finanzapp.domain.CsvArtifact;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.importcsv.CsvImportException;
import com.example.finanzapp.importcsv.CsvImportResult;
import com.example.finanzapp.importcsv.CsvUploadService;
import com.example.finanzapp.repository.CsvArtifactRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import com.example.finanzapp.transactions.TransactionRow;
import com.example.finanzapp.transactions.TransactionViewService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OverviewController {
  private static final DateTimeFormatter IMPORT_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;
  private final CsvArtifactRepository csvArtifactRepository;
  private final CsvUploadService csvUploadService;
  private final TransactionViewService transactionViewService;

  public OverviewController(
      UserRepository userRepository,
      TransactionRepository transactionRepository,
      CsvArtifactRepository csvArtifactRepository,
      CsvUploadService csvUploadService,
      TransactionViewService transactionViewService) {
    this.userRepository = userRepository;
    this.transactionRepository = transactionRepository;
    this.csvArtifactRepository = csvArtifactRepository;
    this.csvUploadService = csvUploadService;
    this.transactionViewService = transactionViewService;
  }

  @GetMapping("/overview")
  public String overview(Model model, @AuthenticationPrincipal UserDetails userDetails) {
    model.addAttribute("pageTitle", "page.overview");
    model.addAttribute("displayName", "User");
    model.addAttribute("transactionCount", 0L);
    model.addAttribute("importCount", 0L);
    model.addAttribute("lastImportLabel", "No imports yet");
    model.addAttribute("lastImportFile", null);
    model.addAttribute("currentBalanceAmount", null);
    model.addAttribute("transactions", java.util.List.of());
    model.addAttribute("transactionsEmpty", true);

    Optional<User> user = resolveUser(userDetails);
    if (user.isPresent()) {
      User currentUser = user.get();
      model.addAttribute("displayName", resolveDisplayName(currentUser));
      model.addAttribute("transactionCount", transactionRepository.countByUserAndDeletedAtIsNull(currentUser));
      model.addAttribute("importCount", csvArtifactRepository.countByUserAndDeletedAtIsNull(currentUser));
      model.addAttribute("currentBalanceAmount", transactionViewService.loadCurrentBalanceLabel(userDetails));
      java.util.List<TransactionRow> recentRows =
          transactionViewService.loadRecent(userDetails, 5);
      model.addAttribute("transactions", recentRows);
      model.addAttribute("transactionsEmpty", recentRows.isEmpty());

      Optional<CsvArtifact> lastImport =
          csvArtifactRepository.findTopByUserAndDeletedAtIsNullOrderByUploadedAtDesc(currentUser);
      if (lastImport.isPresent()) {
        CsvArtifact artifact = lastImport.get();
        model.addAttribute("lastImportLabel", IMPORT_FORMATTER.format(artifact.getUploadedAt()));
        model.addAttribute("lastImportFile", artifact.getOriginalFileName());
      }
    }
    return "overview";
  }

  @PostMapping("/overview/import-csv")
  public String importCsv(
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    String email = userDetails == null ? null : userDetails.getUsername();
    try {
      CsvImportResult result = csvUploadService.importForEmail(email, file);
      redirectAttributes.addFlashAttribute("csvImportStatus", "success");
      redirectAttributes.addFlashAttribute(
          "csvImportMessage", result.importedCount() + " transactions imported.");
      addDuplicateNotice(result, redirectAttributes);
    } catch (CsvImportException ex) {
      redirectAttributes.addFlashAttribute("csvImportStatus", "error");
      redirectAttributes.addFlashAttribute("csvImportMessage", ex.getMessage());
    }
    return "redirect:/overview";
  }

  private Optional<User> resolveUser(UserDetails userDetails) {
    if (userDetails == null || userDetails.getUsername() == null) {
      return Optional.empty();
    }
    return userRepository.findByEmail(userDetails.getUsername());
  }

  private String resolveDisplayName(User user) {
    String displayName = user.getDisplayName();
    if (displayName != null && !displayName.isBlank()) {
      return displayName.trim();
    }
    String email = user.getEmail();
    if (email != null && email.contains("@")) {
      return email.substring(0, email.indexOf('@'));
    }
    return "User";
  }

  private void addDuplicateNotice(CsvImportResult result, RedirectAttributes redirectAttributes) {
    if (result.duplicateCount() <= 0) {
      return;
    }
    redirectAttributes.addFlashAttribute("csvImportDuplicates", result.duplicateSamples());
    redirectAttributes.addFlashAttribute("csvImportDuplicateCount", result.duplicateCount());
  }
}
