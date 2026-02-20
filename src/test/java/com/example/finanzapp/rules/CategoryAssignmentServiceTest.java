package com.example.finanzapp.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.finanzapp.categories.CategoryBootstrapService;
import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.CategoryAssignedBy;
import com.example.finanzapp.domain.Rule;
import com.example.finanzapp.domain.RuleMatchField;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.RuleRepository;
import com.example.finanzapp.repository.TransactionRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryAssignmentServiceTest {
  @Mock
  private CategoryBootstrapService categoryBootstrapService;

  @Mock
  private RuleRepository ruleRepository;

  @Mock
  private TransactionRepository transactionRepository;

  private CategoryAssignmentService service;

  @BeforeEach
  void setUp() {
    service = new CategoryAssignmentService(
        categoryBootstrapService,
        ruleRepository,
        transactionRepository,
        new RuleEngine(new RuleTextNormalizer()));
  }

  @Test
  void assignForImportSetsDefaultCategoryWhenNoRuleMatches() {
    User user = new User();
    Category defaultCategory = new Category();

    Transaction transaction = new Transaction();
    transaction.setUser(user);
    transaction.setPartnerName("Unknown merchant");

    when(categoryBootstrapService.ensureDefaultUncategorized(user)).thenReturn(defaultCategory);
    when(ruleRepository.findByUserAndIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user))
        .thenReturn(List.of());

    service.assignForImport(user, List.of(transaction));

    assertThat(transaction.getCategory()).isEqualTo(defaultCategory);
    assertThat(transaction.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.DEFAULT);
    assertThat(transaction.isCategoryLocked()).isFalse();
    assertThat(transaction.getRuleConflicts()).isNull();
  }

  @Test
  void assignForImportAppliesFirstMatchingRuleAndStoresConflicts() throws Exception {
    User user = new User();
    Category defaultCategory = new Category();
    Category fastFood = new Category();

    Rule first = ruleWith(100, "FastFood", "mcdonalds", RuleMatchField.PARTNER_NAME, fastFood);
    Rule second = ruleWith(200, "Contains Don", "donald", RuleMatchField.BOTH, defaultCategory);

    Transaction transaction = new Transaction();
    transaction.setUser(user);
    transaction.setPartnerName("McDonald’s Berlin");
    transaction.setPurposeText("Lunch");

    when(categoryBootstrapService.ensureDefaultUncategorized(user)).thenReturn(defaultCategory);
    when(ruleRepository.findByUserAndIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user))
        .thenReturn(List.of(first, second));

    service.assignForImport(user, List.of(transaction));

    assertThat(transaction.getCategory()).isEqualTo(fastFood);
    assertThat(transaction.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.RULE);
    assertThat(transaction.getRuleConflicts()).isEqualTo("[200]");
  }

  @Test
  void assignForImportDoesNotOverrideManualLockedTransaction() {
    User user = new User();
    Category manualCategory = new Category();
    Category defaultCategory = new Category();

    Transaction transaction = new Transaction();
    transaction.setUser(user);
    transaction.setPartnerName("McDonald’s Berlin");
    transaction.setCategory(manualCategory);
    transaction.setCategoryAssignedBy(CategoryAssignedBy.MANUAL);
    transaction.setCategoryLocked(true);
    transaction.setRuleConflicts("[123]");

    when(categoryBootstrapService.ensureDefaultUncategorized(user)).thenReturn(defaultCategory);
    when(ruleRepository.findByUserAndIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user))
        .thenReturn(List.of());

    service.assignForImport(user, List.of(transaction));

    assertThat(transaction.getCategory()).isEqualTo(manualCategory);
    assertThat(transaction.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.MANUAL);
    assertThat(transaction.isCategoryLocked()).isTrue();
    assertThat(transaction.getRuleConflicts()).isEqualTo("[123]");
  }

  @Test
  void runSingleRuleUpdatesMetadataAndSavesMatchingTransactions() throws Exception {
    User user = new User();
    Rule rule = ruleWith(100, "FastFood", "mcdonalds", RuleMatchField.PARTNER_NAME, new Category());

    Transaction matching = new Transaction();
    matching.setUser(user);
    matching.setPartnerName("McDonald’s Berlin");

    Transaction nonMatching = new Transaction();
    nonMatching.setUser(user);
    nonMatching.setPartnerName("Other Store");

    when(ruleRepository.findByIdAndUserAndDeletedAtIsNull(100, user)).thenReturn(Optional.of(rule));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeAsc(user))
        .thenReturn(List.of(matching, nonMatching));

    CategoryAssignmentService.RuleRunStats result = service.runSingleRule(user, 100);

    assertThat(result.updatedTransactions()).isEqualTo(1);
    assertThat(result.scannedTransactions()).isEqualTo(2);
    assertThat(matching.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.RULE);
    verify(transactionRepository).saveAll(anyList());
    verify(ruleRepository).save(rule);
    assertThat(rule.getLastRunAt()).isNotNull();
    assertThat(rule.getLastMatchCount()).isEqualTo(1);
  }

  @Test
  void runSingleRuleSkipsSaveWhenNothingChanges() throws Exception {
    User user = new User();
    Rule rule = ruleWith(100, "FastFood", "mcdonalds", RuleMatchField.PARTNER_NAME, new Category());

    Transaction nonMatching = new Transaction();
    nonMatching.setUser(user);
    nonMatching.setPartnerName("Other Store");

    when(ruleRepository.findByIdAndUserAndDeletedAtIsNull(100, user)).thenReturn(Optional.of(rule));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeAsc(user))
        .thenReturn(List.of(nonMatching));

    CategoryAssignmentService.RuleRunStats result = service.runSingleRule(user, 100);

    assertThat(result.updatedTransactions()).isZero();
    verify(transactionRepository, never()).saveAll(any());
    verify(ruleRepository).save(rule);
    assertThat(rule.getLastMatchCount()).isZero();
  }

  private Rule ruleWith(
      int id,
      String name,
      String matchText,
      RuleMatchField field,
      Category category) throws Exception {
    Rule rule = new Rule();
    rule.setName(name);
    rule.setMatchText(matchText);
    rule.setMatchField(field);
    rule.setCategory(category);
    rule.setActive(true);

    Field idField = Rule.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(rule, id);
    return rule;
  }
}
