package com.example.finanzapp.rules;

import com.example.finanzapp.categories.CategoryBootstrapService;
import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.CategoryAssignedBy;
import com.example.finanzapp.domain.Rule;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.RuleRepository;
import com.example.finanzapp.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryAssignmentService {
  private final CategoryBootstrapService categoryBootstrapService;
  private final RuleRepository ruleRepository;
  private final TransactionRepository transactionRepository;
  private final RuleEngine ruleEngine;

  public CategoryAssignmentService(
      CategoryBootstrapService categoryBootstrapService,
      RuleRepository ruleRepository,
      TransactionRepository transactionRepository,
      RuleEngine ruleEngine) {
    this.categoryBootstrapService = categoryBootstrapService;
    this.ruleRepository = ruleRepository;
    this.transactionRepository = transactionRepository;
    this.ruleEngine = ruleEngine;
  }

  public void assignForImport(User user, List<Transaction> transactions) {
    if (transactions == null || transactions.isEmpty()) {
      return;
    }

    Category defaultCategory = categoryBootstrapService.ensureDefaultUncategorized(user);
    List<Rule> activeRules = ruleRepository.findByUserAndIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user);
    for (Transaction transaction : transactions) {
      applyRules(transaction, activeRules, defaultCategory);
    }
  }

  @Transactional
  public RuleRunStats runAllRules(User user) {
    Category defaultCategory = categoryBootstrapService.ensureDefaultUncategorized(user);
    List<Rule> activeRules = ruleRepository.findByUserAndIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user);
    List<Transaction> transactions = transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeAsc(user);

    int updated = 0;
    for (Transaction transaction : transactions) {
      if (applyRules(transaction, activeRules, defaultCategory)) {
        updated++;
      }
    }

    if (updated > 0) {
      transactionRepository.saveAll(transactions);
    }
    return new RuleRunStats(updated, transactions.size());
  }

  @Transactional
  public RuleRunStats runSingleRule(User user, Integer ruleId) {
    Rule rule = ruleRepository.findByIdAndUserAndDeletedAtIsNull(ruleId, user)
        .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

    List<Transaction> transactions = transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeAsc(user);
    int updated = 0;
    int matches = 0;

    for (Transaction transaction : transactions) {
      if (transaction.isCategoryLocked()) {
        continue;
      }

      RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(rule));
      if (evaluation.winningRule() == null) {
        continue;
      }

      matches++;
      if (applyWinningRule(transaction, rule, null)) {
        updated++;
      }
    }

    if (updated > 0) {
      transactionRepository.saveAll(transactions);
    }

    rule.setLastRunAt(Instant.now());
    rule.setLastMatchCount(matches);
    ruleRepository.save(rule);
    return new RuleRunStats(updated, transactions.size());
  }

  boolean applyRules(Transaction transaction, List<Rule> orderedRules, Category defaultCategory) {
    if (transaction.isCategoryLocked()) {
      return false;
    }

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, orderedRules);
    if (evaluation.winningRule() == null) {
      return applyDefaultCategory(transaction, defaultCategory);
    }

    String conflicts = serializeConflictIds(evaluation.conflictRuleIds());
    return applyWinningRule(transaction, evaluation.winningRule(), conflicts);
  }

  private boolean applyWinningRule(Transaction transaction, Rule winningRule, String conflicts) {
    boolean changed =
        !Objects.equals(transaction.getCategory(), winningRule.getCategory())
            || transaction.getCategoryAssignedBy() != CategoryAssignedBy.RULE
            || transaction.isCategoryLocked()
            || !Objects.equals(transaction.getRuleConflicts(), conflicts);

    transaction.setCategory(winningRule.getCategory());
    transaction.setCategoryAssignedBy(CategoryAssignedBy.RULE);
    transaction.setCategoryLocked(false);
    transaction.setRuleConflicts(conflicts);
    return changed;
  }

  private boolean applyDefaultCategory(Transaction transaction, Category defaultCategory) {
    boolean changed =
        !Objects.equals(transaction.getCategory(), defaultCategory)
            || transaction.getCategoryAssignedBy() != CategoryAssignedBy.DEFAULT
            || transaction.isCategoryLocked()
            || transaction.getRuleConflicts() != null;

    transaction.setCategory(defaultCategory);
    transaction.setCategoryAssignedBy(CategoryAssignedBy.DEFAULT);
    transaction.setCategoryLocked(false);
    transaction.setRuleConflicts(null);
    return changed;
  }

  private String serializeConflictIds(List<Integer> conflictRuleIds) {
    if (conflictRuleIds == null || conflictRuleIds.isEmpty()) {
      return null;
    }
    return conflictRuleIds.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(",", "[", "]"));
  }

  public record RuleRunStats(int updatedTransactions, int scannedTransactions) {}
}
