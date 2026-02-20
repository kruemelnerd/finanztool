package com.example.finanzapp.transactions;

import java.math.BigDecimal;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
      @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
      @RequestParam(name = "maxAmount", required = false) BigDecimal maxAmount,
      @RequestParam(name = "nameContains", required = false) String nameContains,
      @RequestParam(name = "purposeContains", required = false) String purposeContains,
      @RequestParam(name = "onlyUncategorized", required = false, defaultValue = "false") boolean onlyUncategorized,
      @RequestParam(name = "page", required = false) Integer page) {
    model.addAttribute("pageTitle", "page.transactions");
    TransactionPage transactionPage = transactionViewService.loadTransactionsPage(
        userDetails,
        minAmount,
        maxAmount,
        nameContains,
        purposeContains,
        onlyUncategorized,
        page,
        PAGE_SIZE);
    model.addAttribute("transactions", transactionPage.rows());
    model.addAttribute("transactionsEmpty", transactionPage.rows().isEmpty());
    model.addAttribute("categoryOptions", transactionViewService.loadCategoryOptions(userDetails));
    addPaginationModel(model, transactionPage);
    model.addAttribute("minAmount", minAmount);
    model.addAttribute("maxAmount", maxAmount);
    model.addAttribute("nameContains", nameContains);
    model.addAttribute("purposeContains", purposeContains);
    model.addAttribute("onlyUncategorized", onlyUncategorized);
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
      @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
      @RequestParam(name = "maxAmount", required = false) BigDecimal maxAmount,
      @RequestParam(name = "nameContains", required = false) String nameContains,
      @RequestParam(name = "purposeContains", required = false) String purposeContains,
      @RequestParam(name = "onlyUncategorized", required = false, defaultValue = "false") boolean onlyUncategorized,
      @RequestParam(name = "page", required = false) Integer page,
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

    String partialRedirect = UriComponentsBuilder.fromPath("/partials/transactions-table")
        .queryParam("minAmount", minAmount)
        .queryParam("maxAmount", maxAmount)
        .queryParam("nameContains", nameContains)
        .queryParam("purposeContains", purposeContains)
        .queryParam("onlyUncategorized", onlyUncategorized)
        .queryParam("page", page)
        .build()
        .toUriString();

    if (hxRequest != null) {
      return "redirect:" + partialRedirect;
    }

    String fullRedirect = UriComponentsBuilder.fromPath("/transactions")
        .queryParam("minAmount", minAmount)
        .queryParam("maxAmount", maxAmount)
        .queryParam("nameContains", nameContains)
        .queryParam("purposeContains", purposeContains)
        .queryParam("onlyUncategorized", onlyUncategorized)
        .queryParam("page", page)
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

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
