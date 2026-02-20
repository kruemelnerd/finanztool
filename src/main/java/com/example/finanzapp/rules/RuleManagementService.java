package com.example.finanzapp.rules;

import com.example.finanzapp.categories.CategoryBootstrapService;
import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.Rule;
import com.example.finanzapp.domain.RuleMatchField;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.CategoryRepository;
import com.example.finanzapp.repository.RuleRepository;
import com.example.finanzapp.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleManagementService {
  private static final DateTimeFormatter DATE_TIME_EN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final DateTimeFormatter DATE_TIME_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private final UserRepository userRepository;
  private final RuleRepository ruleRepository;
  private final CategoryRepository categoryRepository;
  private final CategoryBootstrapService categoryBootstrapService;
  private final CategoryAssignmentService categoryAssignmentService;

  public RuleManagementService(
      UserRepository userRepository,
      RuleRepository ruleRepository,
      CategoryRepository categoryRepository,
      CategoryBootstrapService categoryBootstrapService,
      CategoryAssignmentService categoryAssignmentService) {
    this.userRepository = userRepository;
    this.ruleRepository = ruleRepository;
    this.categoryRepository = categoryRepository;
    this.categoryBootstrapService = categoryBootstrapService;
    this.categoryAssignmentService = categoryAssignmentService;
  }

  @Transactional(readOnly = true)
  public List<RuleListRow> loadRules(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return List.of();
    }

    List<Rule> rules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    Locale locale = resolveLocale(user.get());
    DateTimeFormatter formatter = resolveDateTimeFormatter(locale);

    return java.util.stream.IntStream.range(0, rules.size())
        .mapToObj(index -> toRow(rules.get(index), index, rules.size(), formatter))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<RuleCategoryOption> loadCategoryOptions(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return List.of();
    }

    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user.get());
    if (parents.isEmpty()) {
      return List.of();
    }

    List<RuleCategoryOption> options = new java.util.ArrayList<>();
    for (Category parent : parents) {
      List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), parent);
      for (Category child : children) {
        if (child.getId() == null) {
          continue;
        }
        options.add(new RuleCategoryOption(child.getId(), parent.getName() + " -> " + child.getName()));
      }
    }
    return List.copyOf(options);
  }

  @Transactional(readOnly = true)
  public Optional<RuleFormData> loadRuleFormData(UserDetails userDetails, Integer ruleId) {
    if (ruleId == null) {
      return Optional.empty();
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return Optional.empty();
    }

    return ruleRepository.findByIdAndUserAndDeletedAtIsNull(ruleId, user.get())
        .map(rule -> new RuleFormData(
            rule.getName(),
            rule.getMatchText(),
            rule.getMatchField() == null ? RuleMatchField.BOTH : rule.getMatchField(),
            rule.getCategory() == null ? null : rule.getCategory().getId()));
  }

  @Transactional
  public boolean createRule(UserDetails userDetails, RuleCommand command) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return false;
    }

    RulePreparedData prepared = prepareRuleData(user.get(), command);
    if (prepared == null) {
      return false;
    }

    Rule rule = new Rule();
    rule.setUser(user.get());
    rule.setName(prepared.name());
    rule.setMatchText(prepared.matchText());
    rule.setMatchField(prepared.matchField());
    rule.setCategory(prepared.category());
    rule.setActive(true);
    rule.setSortOrder(ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get()).size());

    try {
      ruleRepository.save(rule);
      return true;
    } catch (DataIntegrityViolationException ignored) {
      return false;
    }
  }

  @Transactional
  public boolean updateRule(UserDetails userDetails, Integer ruleId, RuleCommand command) {
    if (ruleId == null) {
      return false;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return false;
    }

    Optional<Rule> existing = ruleRepository.findByIdAndUserAndDeletedAtIsNull(ruleId, user.get());
    if (existing.isEmpty()) {
      return false;
    }

    RulePreparedData prepared = prepareRuleData(user.get(), command);
    if (prepared == null) {
      return false;
    }

    Rule rule = existing.get();
    rule.setName(prepared.name());
    rule.setMatchText(prepared.matchText());
    rule.setMatchField(prepared.matchField());
    rule.setCategory(prepared.category());

    try {
      ruleRepository.save(rule);
      return true;
    } catch (DataIntegrityViolationException ignored) {
      return false;
    }
  }

  @Transactional
  public boolean toggleRule(UserDetails userDetails, Integer ruleId) {
    Optional<Rule> rule = resolveRule(userDetails, ruleId);
    if (rule.isEmpty()) {
      return false;
    }

    rule.get().setActive(!rule.get().isActive());
    ruleRepository.save(rule.get());
    return true;
  }

  @Transactional
  public boolean moveRuleUp(UserDetails userDetails, Integer ruleId) {
    return moveRule(userDetails, ruleId, -1);
  }

  @Transactional
  public boolean moveRuleDown(UserDetails userDetails, Integer ruleId) {
    return moveRule(userDetails, ruleId, 1);
  }

  @Transactional
  public boolean softDeleteRule(UserDetails userDetails, Integer ruleId) {
    Optional<Rule> rule = resolveRule(userDetails, ruleId);
    if (rule.isEmpty()) {
      return false;
    }

    Rule toDelete = rule.get();
    toDelete.setDeletedAt(Instant.now());
    ruleRepository.save(toDelete);

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return true;
    }
    reindexRules(ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get()));
    return true;
  }

  @Transactional
  public Optional<CategoryAssignmentService.RuleRunStats> runSingleRule(UserDetails userDetails, Integer ruleId) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty() || ruleId == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(categoryAssignmentService.runSingleRule(user.get(), ruleId));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  @Transactional
  public Optional<CategoryAssignmentService.RuleRunStats> runAllRules(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(categoryAssignmentService.runAllRules(user.get()));
  }

  private Optional<Rule> resolveRule(UserDetails userDetails, Integer ruleId) {
    if (ruleId == null) {
      return Optional.empty();
    }
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return Optional.empty();
    }
    return ruleRepository.findByIdAndUserAndDeletedAtIsNull(ruleId, user.get());
  }

  private boolean moveRule(UserDetails userDetails, Integer ruleId, int delta) {
    if (ruleId == null) {
      return false;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return false;
    }

    List<Rule> rules = new java.util.ArrayList<>(
        ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get()));
    int index = findRuleIndex(rules, ruleId);
    if (index < 0) {
      return false;
    }

    int targetIndex = index + delta;
    if (targetIndex < 0 || targetIndex >= rules.size()) {
      return false;
    }

    Collections.swap(rules, index, targetIndex);
    reindexRules(rules);
    return true;
  }

  private void reindexRules(List<Rule> rules) {
    for (int i = 0; i < rules.size(); i++) {
      rules.get(i).setSortOrder(i);
    }
    ruleRepository.saveAll(rules);
  }

  private int findRuleIndex(List<Rule> rules, Integer ruleId) {
    for (int i = 0; i < rules.size(); i++) {
      if (ruleId.equals(rules.get(i).getId())) {
        return i;
      }
    }
    return -1;
  }

  private RulePreparedData prepareRuleData(User user, RuleCommand command) {
    if (command == null) {
      return null;
    }

    String name = normalizeText(command.name());
    String matchText = normalizeText(command.matchText());
    RuleMatchField matchField = command.matchField() == null ? RuleMatchField.BOTH : command.matchField();

    if (name.isBlank() || matchText.isBlank()) {
      return null;
    }

    Optional<Category> category = resolveSubCategory(user, command.categoryId());
    if (category.isEmpty()) {
      return null;
    }

    return new RulePreparedData(name, matchText, matchField, category.get());
  }

  private Optional<Category> resolveSubCategory(User user, Integer categoryId) {
    if (categoryId == null) {
      return Optional.empty();
    }
    Optional<Category> category = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user);
    if (category.isEmpty() || category.get().getParent() == null) {
      return Optional.empty();
    }
    return category;
  }

  private Optional<User> resolveUser(UserDetails userDetails) {
    if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(userDetails.getUsername())
        .map(user -> {
          categoryBootstrapService.ensureDefaultUncategorized(user);
          return user;
        });
  }

  private Locale resolveLocale(User user) {
    if ("DE".equalsIgnoreCase(user.getLanguage())) {
      return Locale.GERMANY;
    }
    return Locale.ENGLISH;
  }

  private DateTimeFormatter resolveDateTimeFormatter(Locale locale) {
    if (Locale.GERMAN.getLanguage().equals(locale.getLanguage())) {
      return DATE_TIME_DE;
    }
    return DATE_TIME_EN;
  }

  private RuleListRow toRow(Rule rule, int index, int size, DateTimeFormatter formatter) {
    String categoryLabel = resolveCategoryLabel(rule.getCategory());
    String lastRun = "-";
    if (rule.getLastRunAt() != null) {
      lastRun = formatter.format(rule.getLastRunAt().atZone(ZoneId.systemDefault()));
    }

    RuleMatchField matchField = rule.getMatchField() == null ? RuleMatchField.BOTH : rule.getMatchField();
    return new RuleListRow(
        rule.getId(),
        rule.getName(),
        rule.getMatchText(),
        matchField,
        categoryLabel,
        rule.isActive(),
        lastRun,
        rule.getLastMatchCount(),
        index > 0,
        index < (size - 1));
  }

  private String resolveCategoryLabel(Category category) {
    if (category == null) {
      return "-";
    }
    if (category.getParent() == null) {
      return category.getName();
    }
    return category.getParent().getName() + " -> " + category.getName();
  }

  private String normalizeText(String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }

  private record RulePreparedData(String name, String matchText, RuleMatchField matchField, Category category) {}

  public record RuleCommand(String name, String matchText, RuleMatchField matchField, Integer categoryId) {}

  public record RuleListRow(
      Integer id,
      String name,
      String matchText,
      RuleMatchField matchField,
      String categoryLabel,
      boolean active,
      String lastRunLabel,
      int lastMatchCount,
      boolean canMoveUp,
      boolean canMoveDown) {}

  public record RuleCategoryOption(Integer id, String label) {}

  public record RuleFormData(String name, String matchText, RuleMatchField matchField, Integer categoryId) {}
}
