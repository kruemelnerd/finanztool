package com.example.finanzapp.rules;

import java.util.List;
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
      @RequestParam(name = "categoryId", required = false) Integer categoryId,
      Model model,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    List<RuleManagementService.RuleCategoryOption> options = ruleManagementService.loadCategoryOptions(userDetails);
    if (options.isEmpty()) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.generic"));
      return "redirect:/rules";
    }

    if (categoryId == null) {
      model.addAttribute("pageTitle", "page.rules");
      model.addAttribute("categoryOptions", options);
      return "rule-category-select";
    }

    Optional<String> label = options.stream()
        .filter(option -> option.id().equals(categoryId))
        .map(RuleManagementService.RuleCategoryOption::label)
        .findFirst();
    if (label.isEmpty()) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.validation"));
      return "redirect:/rules/new";
    }

    model.addAttribute("pageTitle", "page.rules");
    model.addAttribute("editMode", false);
    model.addAttribute("categoryId", categoryId);
    model.addAttribute("categoryLabel", label.get());
    model.addAttribute("fragmentsText", "");
    return "rule-fragments-form";
  }

  @GetMapping("/rules/{categoryId}/edit")
  public String editRuleGroup(
      @PathVariable("categoryId") Integer categoryId,
      Model model,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    Optional<RuleManagementService.RuleGroupFormData> formData =
        ruleManagementService.loadRuleGroupFormData(userDetails, categoryId);
    if (formData.isEmpty()) {
      redirectAttributes.addFlashAttribute("rulesStatus", "error");
      redirectAttributes.addFlashAttribute("rulesMessage", msg("rules.error.notFound"));
      return "redirect:/rules";
    }

    model.addAttribute("pageTitle", "page.rules");
    model.addAttribute("editMode", true);
    model.addAttribute("categoryId", formData.get().categoryId());
    model.addAttribute("categoryLabel", formData.get().categoryLabel());
    model.addAttribute("fragmentsText", formData.get().fragmentsText());
    return "rule-fragments-form";
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
}
