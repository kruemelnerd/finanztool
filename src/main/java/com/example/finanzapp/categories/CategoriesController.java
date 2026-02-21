package com.example.finanzapp.categories;

import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CategoriesController {
  private final CategoryManagementService categoryManagementService;
  private final MessageSource messageSource;

  public CategoriesController(
      CategoryManagementService categoryManagementService,
      MessageSource messageSource) {
    this.categoryManagementService = categoryManagementService;
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

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
