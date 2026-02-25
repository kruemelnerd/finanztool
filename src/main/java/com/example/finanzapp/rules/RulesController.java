package com.example.finanzapp.rules;

import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RulesController {
  private final RuleManagementService ruleManagementService;
  private final RuleTransferService ruleTransferService;
  private final MessageSource messageSource;

  public RulesController(
      RuleManagementService ruleManagementService,
      RuleTransferService ruleTransferService,
      MessageSource messageSource) {
    this.ruleManagementService = ruleManagementService;
    this.ruleTransferService = ruleTransferService;
    this.messageSource = messageSource;
  }

  @GetMapping("/rules")
  public String rules() {
    return "redirect:/categories";
  }

  @GetMapping("/rules/new")
  public String newRulePage() {
    return "redirect:/categories";
  }

  @GetMapping("/rules/{categoryId}/edit")
  public String editRuleGroupPage(@PathVariable("categoryId") Integer categoryId) {
    return "redirect:/categories";
  }

  @PostMapping("/rules")
  public String createRuleGroup(
      @RequestParam("categoryId") Integer categoryId,
      @RequestParam("fragmentsText") String fragmentsText,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    RuleManagementService.RuleGroupCommand command =
        new RuleManagementService.RuleGroupCommand(categoryId, fragmentsText);
    if (!ruleManagementService.createRuleGroup(userDetails, command)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.validation"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.create.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{categoryId}")
  public String updateRuleGroup(
      @PathVariable("categoryId") Integer categoryId,
      @RequestParam("fragmentsText") String fragmentsText,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    RuleManagementService.RuleGroupCommand command =
        new RuleManagementService.RuleGroupCommand(categoryId, fragmentsText);
    if (!ruleManagementService.updateRuleGroup(userDetails, categoryId, command)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.validation"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.update.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{categoryId}/toggle")
  public String toggleRuleCategory(
      @PathVariable("categoryId") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.toggleRuleCategory(userDetails, categoryId)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.toggle.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{categoryId}/move-up")
  public String moveRuleCategoryUp(
      @PathVariable("categoryId") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.moveRuleCategoryUp(userDetails, categoryId)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.move.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{categoryId}/move-down")
  public String moveRuleCategoryDown(
      @PathVariable("categoryId") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.moveRuleCategoryDown(userDetails, categoryId)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.move.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{categoryId}/run")
  public String runRuleCategory(
      @PathVariable("categoryId") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    Optional<CategoryAssignmentService.RuleRunStats> stats =
        ruleManagementService.runCategoryRules(userDetails, categoryId);
    if (stats.isEmpty()) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute(
        "rulesMessage",
        msg("rules.run.single.success", stats.get().updatedTransactions(), stats.get().scannedTransactions()));
    return "redirect:/rules";
  }

  @PostMapping("/rules/run-all")
  public String runAllRules(
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    Optional<CategoryAssignmentService.RuleRunStats> stats = ruleManagementService.runAllRules(userDetails);
    if (stats.isEmpty()) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.generic"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute(
        "rulesMessage",
        msg("rules.run.all.success", stats.get().updatedTransactions(), stats.get().scannedTransactions()));
    return "redirect:/rules";
  }

  @GetMapping(value = "/rules/export", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<byte[]> exportRules(@AuthenticationPrincipal UserDetails userDetails) {
    Optional<String> exportJson = ruleTransferService.exportAsJson(userDetails);
    if (exportJson.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    byte[] body = exportJson.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rules-export.json\"")
        .body(body);
  }

  @PostMapping("/rules/import")
  public String importRules(
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    RuleTransferService.ImportResult result = ruleTransferService.importFromJson(userDetails, file);

    if (result.status() == RuleTransferService.ImportStatus.SUCCESS) {
      redirectAttributes.addFlashAttribute("rulesStatus", "success");
      redirectAttributes.addFlashAttribute(
          "rulesMessage",
          msg("rules.import.success", result.importedGroupCount(), result.importedRuleCount()));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "error");
    redirectAttributes.addFlashAttribute("rulesMessage", importErrorMessage(result));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{categoryId}/delete")
  public String deleteRuleCategory(
      @PathVariable("categoryId") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.softDeleteRuleCategory(userDetails, categoryId)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.delete.success"));
    return "redirect:/rules";
  }

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  private String importErrorMessage(RuleTransferService.ImportResult result) {
    return switch (result.status()) {
      case USER_NOT_FOUND -> msg("rules.error.generic");
      case EMPTY_FILE -> msg("rules.import.error.emptyFile");
      case INVALID_JSON -> msg("rules.import.error.invalidJson");
      case INVALID_FORMAT -> msg("rules.import.error.invalidFormat");
      case INVALID_RULES -> msg("rules.import.error.invalidRules");
      case UNKNOWN_CATEGORY -> msg("rules.import.error.unknownCategory", result.detail());
      case SUCCESS -> msg("rules.import.success", result.importedGroupCount(), result.importedRuleCount());
    };
  }
}
