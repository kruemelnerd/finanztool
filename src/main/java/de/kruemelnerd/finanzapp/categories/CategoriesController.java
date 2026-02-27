package de.kruemelnerd.finanzapp.categories;

import de.kruemelnerd.finanzapp.rules.RuleManagementService;
import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CategoriesController {
  private final CategoryManagementService categoryManagementService;
  private final CategoryTransferService categoryTransferService;
  private final RuleManagementService ruleManagementService;
  private final MessageSource messageSource;

  public CategoriesController(
      CategoryManagementService categoryManagementService,
      CategoryTransferService categoryTransferService,
      RuleManagementService ruleManagementService,
      MessageSource messageSource) {
    this.categoryManagementService = categoryManagementService;
    this.categoryTransferService = categoryTransferService;
    this.ruleManagementService = ruleManagementService;
    this.messageSource = messageSource;
  }

  @GetMapping("/categories")
  public String categories(
      Model model,
      @AuthenticationPrincipal UserDetails userDetails) {
    CategoryManagementService.CategoryPageData pageData = categoryManagementService.loadCategoryPage(userDetails);
    model.addAttribute("pageTitle", "page.categories");
    model.addAttribute("parents", pageData.parents());
    model.addAttribute("subcategories", pageData.subcategories());
    model.addAttribute("parentOptions", pageData.parentOptions());
    return "categories";
  }

  @PostMapping("/categories/parents")
  public String createParent(
      @RequestParam("name") String name,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.createParent(userDetails, name);
    applyStatus(status, "categories.parent.create.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/parents/{id}")
  public String updateParent(
      @PathVariable("id") Integer categoryId,
      @RequestParam("name") String name,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.updateParent(userDetails, categoryId, name);
    applyStatus(status, "categories.parent.update.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/parents/{id}/move-up")
  public String moveParentUp(
      @PathVariable("id") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.moveParent(userDetails, categoryId, -1);
    applyStatus(status, "categories.move.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/parents/{id}/move-down")
  public String moveParentDown(
      @PathVariable("id") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.moveParent(userDetails, categoryId, 1);
    applyStatus(status, "categories.move.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/subcategories")
  public String createSubcategory(
      @RequestParam("parentId") Integer parentId,
      @RequestParam("name") String name,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.createSubcategory(userDetails, parentId, name);
    applyStatus(status, "categories.subcategory.create.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/subcategories/{id}")
  public String updateSubcategory(
      @PathVariable("id") Integer categoryId,
      @RequestParam("parentId") Integer parentId,
      @RequestParam("name") String name,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status =
        categoryManagementService.updateSubcategory(userDetails, categoryId, parentId, name);
    applyStatus(status, "categories.subcategory.update.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/subcategories/{id}/move-up")
  public String moveSubcategoryUp(
      @PathVariable("id") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.moveSubcategory(userDetails, categoryId, -1);
    applyStatus(status, "categories.move.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/subcategories/{id}/move-down")
  public String moveSubcategoryDown(
      @PathVariable("id") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.moveSubcategory(userDetails, categoryId, 1);
    applyStatus(status, "categories.move.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/parents/{id}/delete")
  public String deleteParent(
      @PathVariable("id") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.deleteParent(userDetails, categoryId);
    applyStatus(status, "categories.parent.delete.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/subcategories/{id}/delete")
  public String deleteSubcategory(
      @PathVariable("id") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.deleteSubcategory(userDetails, categoryId);
    applyStatus(status, "categories.subcategory.delete.success", redirectAttributes);
    return "redirect:/categories";
  }

  @PostMapping("/categories/subcategories/{id}/rule")
  public String upsertSubcategoryRule(
      @PathVariable("id") Integer categoryId,
      @RequestParam("fragmentsText") String fragmentsText,
      @RequestParam(name = "active", defaultValue = "false") boolean active,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    RuleManagementService.RuleGroupUpdateStatus status =
        ruleManagementService.upsertRuleGroupForCategory(userDetails, categoryId, fragmentsText, active);

    if (status == RuleManagementService.RuleGroupUpdateStatus.SUCCESS) {
      redirectAttributes.addFlashAttribute("categoriesStatus", "success");
      redirectAttributes.addFlashAttribute("categoriesMessage", msg("categories.rule.save.success"));
      return "redirect:/categories";
    }

    redirectAttributes.addFlashAttribute("categoriesStatus", "error");
    redirectAttributes.addFlashAttribute("categoriesMessage", msg(ruleErrorMessageKey(status)));
    return "redirect:/categories";
  }

  @PostMapping("/categories/subcategories/{id}/rule/delete")
  public String deleteSubcategoryRule(
      @PathVariable("id") Integer categoryId,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    if (!ruleManagementService.softDeleteRuleCategory(userDetails, categoryId)) {
      redirectAttributes.addFlashAttribute("categoriesStatus", "error");
      redirectAttributes.addFlashAttribute("categoriesMessage", msg("categories.rule.error.notFound"));
      return "redirect:/categories";
    }

    redirectAttributes.addFlashAttribute("categoriesStatus", "success");
    redirectAttributes.addFlashAttribute("categoriesMessage", msg("categories.rule.delete.success"));
    return "redirect:/categories";
  }

  @GetMapping(value = "/categories/export", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<byte[]> exportCategories(@AuthenticationPrincipal UserDetails userDetails) {
    return categoryTransferService.exportAsJson(userDetails)
        .map(json -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"categories-export.json\"")
            .body(json.getBytes(StandardCharsets.UTF_8)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/categories/import")
  public String importCategories(
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    CategoryTransferService.ImportResult result = categoryTransferService.importFromJson(userDetails, file);
    if (result.status() == CategoryTransferService.ImportStatus.SUCCESS) {
      redirectAttributes.addFlashAttribute("categoriesStatus", "success");
      redirectAttributes.addFlashAttribute(
          "categoriesMessage",
          msg("categories.import.success", result.importedParentCount(), result.importedSubcategoryCount()));
      return "redirect:/categories";
    }

    redirectAttributes.addFlashAttribute("categoriesStatus", "error");
    redirectAttributes.addFlashAttribute("categoriesMessage", importErrorMessage(result));
    return "redirect:/categories";
  }

  @PostMapping("/categories/reorder")
  public ResponseEntity<Void> reorder(
      @RequestBody CategoryManagementService.CategoryReorderCommand command,
      @AuthenticationPrincipal UserDetails userDetails) {
    CategoryManagementService.UpdateStatus status = categoryManagementService.reorder(userDetails, command);
    if (status == CategoryManagementService.UpdateStatus.SUCCESS) {
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.badRequest().build();
  }

  private void applyStatus(
      CategoryManagementService.UpdateStatus status,
      String successMessageKey,
      RedirectAttributes redirectAttributes) {
    if (status == CategoryManagementService.UpdateStatus.SUCCESS) {
      redirectAttributes.addFlashAttribute("categoriesStatus", "success");
      redirectAttributes.addFlashAttribute("categoriesMessage", msg(successMessageKey));
      return;
    }

    redirectAttributes.addFlashAttribute("categoriesStatus", "error");
    redirectAttributes.addFlashAttribute("categoriesMessage", msg(errorMessageKey(status)));
  }

  private String errorMessageKey(CategoryManagementService.UpdateStatus status) {
    return switch (status) {
      case USER_NOT_FOUND -> "categories.error.userNotFound";
      case CATEGORY_NOT_FOUND -> "categories.error.notFound";
      case PARENT_NOT_FOUND -> "categories.error.parentNotFound";
      case INVALID_NAME -> "categories.error.invalidName";
      case DUPLICATE_NAME -> "categories.error.duplicateName";
      case SYSTEM_PROTECTED -> "categories.error.systemProtected";
      case CATEGORY_IN_USE -> "categories.error.inUse";
      case CANNOT_MOVE -> "categories.error.moveNotPossible";
      case INVALID_REORDER -> "categories.error.reorder";
      case SUCCESS -> "categories.error.generic";
    };
  }

  private String ruleErrorMessageKey(RuleManagementService.RuleGroupUpdateStatus status) {
    return switch (status) {
      case USER_NOT_FOUND, CATEGORY_NOT_FOUND -> "categories.rule.error.notFound";
      case INVALID_FRAGMENTS -> "categories.rule.error.invalidFragments";
      case PERSISTENCE_ERROR -> "categories.rule.error.generic";
      case SUCCESS -> "categories.rule.error.generic";
    };
  }

  private String importErrorMessage(CategoryTransferService.ImportResult result) {
    return switch (result.status()) {
      case USER_NOT_FOUND -> msg("categories.error.userNotFound");
      case EMPTY_FILE -> msg("categories.import.error.emptyFile");
      case INVALID_JSON -> msg("categories.import.error.invalidJson");
      case INVALID_FORMAT -> msg("categories.import.error.invalidFormat");
      case INVALID_CATEGORIES -> msg("categories.import.error.invalidCategories");
      case SUCCESS -> msg("categories.import.success", result.importedParentCount(), result.importedSubcategoryCount());
    };
  }

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
