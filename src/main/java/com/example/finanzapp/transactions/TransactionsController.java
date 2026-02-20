package com.example.finanzapp.transactions;

import java.math.BigDecimal;
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
      @RequestParam(name = "page", required = false) Integer page) {
    model.addAttribute("pageTitle", "page.transactions");
    TransactionPage transactionPage = transactionViewService.loadTransactionsPage(
        userDetails,
        minAmount,
        maxAmount,
        nameContains,
        purposeContains,
        page,
        PAGE_SIZE);
    model.addAttribute("transactions", transactionPage.rows());
    model.addAttribute("transactionsEmpty", transactionPage.rows().isEmpty());
    addPaginationModel(model, transactionPage);
    model.addAttribute("minAmount", minAmount);
    model.addAttribute("maxAmount", maxAmount);
    model.addAttribute("nameContains", nameContains);
    model.addAttribute("purposeContains", purposeContains);
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
