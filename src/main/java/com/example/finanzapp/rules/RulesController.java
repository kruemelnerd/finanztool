package com.example.finanzapp.rules;

import com.example.finanzapp.domain.RuleMatchField;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RulesController {
  private final RuleManagementService ruleManagementService;
  private final MessageSource messageSource;

  public RulesController(
      RuleManagementService ruleManagementService,
      MessageSource messageSource) {
    this.ruleManagementService = ruleManagementService;
    this.messageSource = messageSource;
  }

  @GetMapping("/rules")
  public String rules(
      Model model,
      @AuthenticationPrincipal UserDetails userDetails) {
    model.addAttribute("pageTitle", "page.rules");
    model.addAttribute("rules", ruleManagementService.loadRules(userDetails));
    return "rules";
  }

  @GetMapping("/rules/new")
  public String newRule(
      Model model,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!prepareRuleFormModel(model, userDetails, null, false, "", "", RuleMatchField.BOTH, null)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.generic"));
      return "redirect:/rules";
    }
    return "rule-form";
  }

  @GetMapping("/rules/{id}/edit")
  public String editRule(
      @PathVariable("id") Integer id,
      Model model,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    Optional<RuleManagementService.RuleFormData> formData = ruleManagementService.loadRuleFormData(userDetails, id);
    if (formData.isEmpty()) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    if (!prepareRuleFormModel(
        model,
        userDetails,
        id,
        true,
        formData.get().name(),
        formData.get().matchText(),
        formData.get().matchField(),
        formData.get().categoryId())) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.generic"));
      return "redirect:/rules";
    }
    return "rule-form";
  }

  @PostMapping("/rules")
  public String createRule(
      @RequestParam("name") String name,
      @RequestParam("matchText") String matchText,
      @RequestParam("matchField") String matchFieldRaw,
      @RequestParam("categoryId") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    RuleManagementService.RuleCommand command = new RuleManagementService.RuleCommand(
        name,
        matchText,
        parseMatchField(matchFieldRaw),
        categoryId);
    if (!ruleManagementService.createRule(userDetails, command)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.validation"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.create.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{id}")
  public String updateRule(
      @PathVariable("id") Integer id,
      @RequestParam("name") String name,
      @RequestParam("matchText") String matchText,
      @RequestParam("matchField") String matchFieldRaw,
      @RequestParam("categoryId") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    RuleManagementService.RuleCommand command = new RuleManagementService.RuleCommand(
        name,
        matchText,
        parseMatchField(matchFieldRaw),
        categoryId);
    if (!ruleManagementService.updateRule(userDetails, id, command)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.validation"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.update.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{id}/toggle")
  public String toggleRule(
      @PathVariable("id") Integer id,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.toggleRule(userDetails, id)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.toggle.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{id}/move-up")
  public String moveRuleUp(
      @PathVariable("id") Integer id,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.moveRuleUp(userDetails, id)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.move.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{id}/move-down")
  public String moveRuleDown(
      @PathVariable("id") Integer id,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.moveRuleDown(userDetails, id)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.move.success"));
    return "redirect:/rules";
  }

  @PostMapping("/rules/{id}/run")
  public String runRule(
      @PathVariable("id") Integer id,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    Optional<CategoryAssignmentService.RuleRunStats> stats = ruleManagementService.runSingleRule(userDetails, id);
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

  @PostMapping("/rules/{id}/delete")
  public String deleteRule(
      @PathVariable("id") Integer id,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.softDeleteRule(userDetails, id)) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    redirectAttributes.addFlashAttribute("rulesStatus", "success");
    redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.delete.success"));
    return "redirect:/rules";
  }

  private boolean prepareRuleFormModel(
      Model model,
      UserDetails userDetails,
      Integer ruleId,
      boolean editMode,
      String name,
      String matchText,
      RuleMatchField matchField,
      Integer categoryId) {
    model.addAttribute("pageTitle", "page.rules");
    model.addAttribute("editMode", editMode);
    model.addAttribute("ruleId", ruleId);
    model.addAttribute("ruleName", name);
    model.addAttribute("matchText", matchText);
    model.addAttribute("selectedMatchField", matchField == null ? RuleMatchField.BOTH.name() : matchField.name());
    model.addAttribute("selectedCategoryId", categoryId);
    model.addAttribute("matchFields", RuleMatchField.values());

    java.util.List<RuleManagementService.RuleCategoryOption> options = ruleManagementService.loadCategoryOptions(userDetails);
    if (options.isEmpty()) {
      return false;
    }
    model.addAttribute("categoryOptions", options);
    return true;
  }

  private RuleMatchField parseMatchField(String raw) {
    if (raw == null || raw.isBlank()) {
      return RuleMatchField.BOTH;
    }

    try {
      return RuleMatchField.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return RuleMatchField.BOTH;
    }
  }

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
