package de.kruemelnerd.finanzapp.transactions;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TransactionsController {
  private static final int PAGE_SIZE = 10;

  private final TransactionViewService transactionViewService;
  private final MessageSource messageSource;

  public TransactionsController(
      TransactionViewService transactionViewService,
      MessageSource messageSource) {
    this.transactionViewService = transactionViewService;
    this.messageSource = messageSource;
  }

  @GetMapping("/transactions")
  public String transactions(
      Model model,
      @AuthenticationPrincipal UserDetails userDetails,
      @ModelAttribute TransactionFilterRequest filter) {
    model.addAttribute("pageTitle", "page.transactions");
    TransactionPage transactionPage = loadTransactionPage(userDetails, filter);
    addRowsAndCategoryOptions(model, userDetails, transactionPage);
    addPaginationModel(model, transactionPage);
    model.addAttribute("minAmount", filter.getMinAmount());
    model.addAttribute("maxAmount", filter.getMaxAmount());
    model.addAttribute("nameContains", filter.getNameContains());
    model.addAttribute("purposeContains", filter.getPurposeContains());
    model.addAttribute("onlyUncategorized", filter.isOnlyUncategorized());
    model.addAttribute("subcategoryId", transactionPage.categoryIdFilter());
    model.addAttribute("parentCategoryId", transactionPage.parentCategoryIdFilter());
    model.addAttribute("categoryFilterLabel", transactionPage.categoryFilterLabel());
    model.addAttribute("currentBalanceAmount", transactionViewService.loadCurrentBalanceLabel(userDetails));
    return "transactions";
  }

  @PostMapping("/transactions/{id}/delete")
  public String delete(
      @PathVariable("id") Integer id,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    boolean deleted = transactionViewService.softDeleteTransaction(userDetails, id);
    if (!deleted) {
      redirectAttributes.addFlashAttribute("transactionStatus", "error");
      redirectAttributes.addFlashAttribute(
          "transactionMessage", msg("transaction.delete.error"));
    } else {
      redirectAttributes.addFlashAttribute("transactionStatus", "success");
      redirectAttributes.addFlashAttribute(
          "transactionMessage", msg("transaction.delete.success"));
    }
    return "redirect:/transactions";
  }

  @PostMapping("/transactions/{id}/set-category")
  public String setCategory(
      @PathVariable("id") Integer id,
      @RequestParam("categoryId") Integer categoryId,
      @ModelAttribute TransactionFilterRequest filter,
      @RequestHeader(name = "HX-Request", required = false) String hxRequest,
      @AuthenticationPrincipal UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    boolean updated = transactionViewService.setManualCategory(userDetails, id, categoryId);
    if (!updated) {
      redirectAttributes.addFlashAttribute("transactionStatus", "error");
      redirectAttributes.addFlashAttribute(
          "transactionMessage", msg("transaction.category.update.error"));
    } else {
      redirectAttributes.addFlashAttribute("transactionStatus", "success");
      redirectAttributes.addFlashAttribute(
          "transactionMessage", msg("transaction.category.update.success"));
    }

    String partialRedirect = filter
        .applyQueryParams(UriComponentsBuilder.fromPath("/partials/transactions-table"))
        .build()
        .toUriString();

    if (hxRequest != null) {
      return "redirect:" + partialRedirect;
    }

    String fullRedirect = filter
        .applyQueryParams(UriComponentsBuilder.fromPath("/transactions"))
        .build()
        .toUriString();
    return "redirect:" + fullRedirect;
  }

  private void addPaginationModel(Model model, TransactionPage transactionPage) {
    model.addAttribute("currentPage", transactionPage.page());
    model.addAttribute("totalPages", transactionPage.totalPages());
    model.addAttribute("totalItems", transactionPage.totalItems());
    model.addAttribute("hasPreviousPage", transactionPage.hasPreviousPage());
    model.addAttribute("hasNextPage", transactionPage.hasNextPage());
    model.addAttribute("previousPage", transactionPage.previousPage());
    model.addAttribute("nextPage", transactionPage.nextPage());
  }

  private TransactionPage loadTransactionPage(
      UserDetails userDetails,
      TransactionFilterRequest filter) {
    return transactionViewService.loadTransactionsPage(
        userDetails,
        filter.getMinAmount(),
        filter.getMaxAmount(),
        filter.getNameContains(),
        filter.getPurposeContains(),
        filter.isOnlyUncategorized(),
        filter.getSubcategoryId(),
        filter.getParentCategoryId(),
        filter.getPage(),
        PAGE_SIZE);
  }

  private void addRowsAndCategoryOptions(
      Model model,
      UserDetails userDetails,
      TransactionPage transactionPage) {
    model.addAttribute("transactions", transactionPage.rows());
    model.addAttribute("transactionsEmpty", transactionPage.rows().isEmpty());
    model.addAttribute("categoryOptions", transactionViewService.loadCategoryOptions(userDetails));
  }

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
