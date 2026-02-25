package com.example.finanzapp.rules;

import com.example.finanzapp.categories.CategoryBootstrapService;
import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.Rule;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.CategoryRepository;
import com.example.finanzapp.repository.RuleRepository;
import com.example.finanzapp.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
  public List<RuleCategoryRow> loadRules(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return List.of();
    }

    List<Rule> orderedRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    if (orderedRules.isEmpty()) {
      return List.of();
    }

    Locale locale = resolveLocale(user.get());
    DateTimeFormatter formatter = resolveDateTimeFormatter(locale);
    Map<Integer, List<Rule>> rulesByCategory = groupRulesByCategory(orderedRules);
    List<Integer> categoryOrder = new ArrayList<>(rulesByCategory.keySet());

    List<RuleCategoryRow> rows = new ArrayList<>();
    for (int index = 0; index < categoryOrder.size(); index++) {
      Integer categoryId = categoryOrder.get(index);
      List<Rule> categoryRules = rulesByCategory.get(categoryId);
      if (categoryRules == null || categoryRules.isEmpty()) {
        continue;
      }

      Rule first = categoryRules.get(0);
      String categoryLabel = resolveCategoryLabel(first.getCategory());
      List<String> fragments = categoryRules.stream()
          .map(Rule::getMatchText)
          .filter(value -> value != null && !value.isBlank())
          .toList();

      boolean allActive = categoryRules.stream().allMatch(Rule::isActive);
      Instant lastRunAt = categoryRules.stream()
          .map(Rule::getLastRunAt)
          .filter(value -> value != null)
          .max(Instant::compareTo)
          .orElse(null);

      String lastRunLabel = "-";
      if (lastRunAt != null) {
        lastRunLabel = formatter.format(lastRunAt.atZone(ZoneId.systemDefault()));
      }

      int matchCount = categoryRules.stream()
          .mapToInt(Rule::getLastMatchCount)
          .sum();

      rows.add(new RuleCategoryRow(
          categoryId,
          categoryLabel,
          fragments,
          fragments.size(),
          allActive,
          lastRunLabel,
          matchCount,
          index > 0,
          index < (categoryOrder.size() - 1)));
    }

    return List.copyOf(rows);
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

    List<RuleCategoryOption> options = new ArrayList<>();
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
  public Optional<RuleGroupFormData> loadRuleGroupFormData(UserDetails userDetails, Integer categoryId) {
    if (categoryId == null) {
      return Optional.empty();
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return Optional.empty();
    }

    List<Rule> rules = ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), categoryId);
    if (rules.isEmpty()) {
      return Optional.empty();
    }

    Rule first = rules.get(0);
    String categoryLabel = resolveCategoryLabel(first.getCategory());
    String fragmentsText = rules.stream()
        .map(Rule::getMatchText)
        .filter(value -> value != null && !value.isBlank())
        .collect(java.util.stream.Collectors.joining("\n"));

    return Optional.of(new RuleGroupFormData(categoryId, categoryLabel, fragmentsText));
  }

  @Transactional
  public boolean createRuleGroup(UserDetails userDetails, RuleGroupCommand command) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return false;
    }

    RuleGroupPreparedData prepared = prepareRuleGroupData(user.get(), command);
    if (prepared == null) {
      return false;
    }

    List<Rule> orderedRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    int nextSortOrder = orderedRules.isEmpty() ? 0 : orderedRules.get(orderedRules.size() - 1).getSortOrder() + 1;

    List<Rule> newRules = new ArrayList<>();
    for (String fragment : prepared.fragments()) {
      Rule rule = new Rule();
      rule.setUser(user.get());
      rule.setName(uniqueRuleName(prepared.category()));
      rule.setMatchText(fragment);
      rule.setMatchField(com.example.finanzapp.domain.RuleMatchField.BOTH);
      rule.setCategory(prepared.category());
      rule.setActive(true);
      rule.setSortOrder(nextSortOrder++);
      newRules.add(rule);
    }

    try {
      ruleRepository.saveAll(newRules);
      normalizeSortOrder(user.get());
      return true;
    } catch (DataIntegrityViolationException ignored) {
      return false;
    }
  }

  @Transactional
  public boolean updateRuleGroup(UserDetails userDetails, Integer categoryId, RuleGroupCommand command) {
    if (categoryId == null) {
      return false;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return false;
    }

    List<Rule> existingRules = ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), categoryId);
    if (existingRules.isEmpty()) {
      return false;
    }

    RuleGroupPreparedData prepared = prepareRuleGroupData(user.get(), command);
    if (prepared == null || !categoryId.equals(prepared.category().getId())) {
      return false;
    }

    List<Rule> allRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    List<Integer> categoryOrder = buildCategoryOrder(allRules);
    boolean allActive = existingRules.stream().allMatch(Rule::isActive);

    Instant deletedAt = Instant.now();
    for (Rule existingRule : existingRules) {
      existingRule.setDeletedAt(deletedAt);
    }
    ruleRepository.saveAll(existingRules);

    int nextSortOrder = allRules.isEmpty() ? 0 : allRules.get(allRules.size() - 1).getSortOrder() + 1;
    List<Rule> replacementRules = new ArrayList<>();
    for (String fragment : prepared.fragments()) {
      Rule rule = new Rule();
      rule.setUser(user.get());
      rule.setName(uniqueRuleName(prepared.category()));
      rule.setMatchText(fragment);
      rule.setMatchField(com.example.finanzapp.domain.RuleMatchField.BOTH);
      rule.setCategory(prepared.category());
      rule.setActive(allActive);
      rule.setSortOrder(nextSortOrder++);
      replacementRules.add(rule);
    }

    try {
      ruleRepository.saveAll(replacementRules);
      reindexByCategoryOrder(user.get(), categoryOrder);
      return true;
    } catch (DataIntegrityViolationException ignored) {
      return false;
    }
  }

  @Transactional
  public boolean toggleRuleCategory(UserDetails userDetails, Integer categoryId) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty() || categoryId == null) {
      return false;
    }

    List<Rule> rules = ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), categoryId);
    if (rules.isEmpty()) {
      return false;
    }

    boolean enable = !rules.stream().allMatch(Rule::isActive);
    for (Rule rule : rules) {
      rule.setActive(enable);
    }
    ruleRepository.saveAll(rules);
    return true;
  }

  @Transactional
  public boolean moveRuleCategoryUp(UserDetails userDetails, Integer categoryId) {
    return moveRuleCategory(userDetails, categoryId, -1);
  }

  @Transactional
  public boolean moveRuleCategoryDown(UserDetails userDetails, Integer categoryId) {
    return moveRuleCategory(userDetails, categoryId, 1);
  }

  @Transactional
  public boolean softDeleteRuleCategory(UserDetails userDetails, Integer categoryId) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty() || categoryId == null) {
      return false;
    }

    List<Rule> rules = ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), categoryId);
    if (rules.isEmpty()) {
      return false;
    }

    Instant now = Instant.now();
    for (Rule rule : rules) {
      rule.setDeletedAt(now);
    }
    ruleRepository.saveAll(rules);
    normalizeSortOrder(user.get());
    return true;
  }

  @Transactional
  public Optional<CategoryAssignmentService.RuleRunStats> runCategoryRules(UserDetails userDetails, Integer categoryId) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty() || categoryId == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(categoryAssignmentService.runCategoryRules(user.get(), categoryId));
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

  @Transactional
  public RuleGroupUpdateStatus upsertRuleGroupForCategory(
      UserDetails userDetails,
      Integer categoryId,
      String fragmentsText,
      boolean active) {
    if (categoryId == null) {
      return RuleGroupUpdateStatus.CATEGORY_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return RuleGroupUpdateStatus.USER_NOT_FOUND;
    }

    Optional<Category> category = resolveSubCategory(user.get(), categoryId);
    if (category.isEmpty()) {
      return RuleGroupUpdateStatus.CATEGORY_NOT_FOUND;
    }

    List<String> fragments = parseFragments(fragmentsText);
    if (fragments.isEmpty()) {
      return RuleGroupUpdateStatus.INVALID_FRAGMENTS;
    }

    List<Rule> existingRules =
        ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), categoryId);
    List<String> existingFragments = existingRules.stream()
        .map(Rule::getMatchText)
        .filter(value -> value != null && !value.isBlank())
        .toList();

    if (!existingRules.isEmpty() && existingFragments.equals(fragments)) {
      boolean changed = false;
      for (Rule existingRule : existingRules) {
        if (existingRule.isActive() != active) {
          existingRule.setActive(active);
          changed = true;
        }
      }
      if (changed) {
        ruleRepository.saveAll(existingRules);
      }
      return RuleGroupUpdateStatus.SUCCESS;
    }

    List<Rule> allRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    List<Integer> categoryOrder = buildCategoryOrder(allRules);

    if (!existingRules.isEmpty()) {
      Instant deletedAt = Instant.now();
      for (Rule existingRule : existingRules) {
        existingRule.setDeletedAt(deletedAt);
      }
      ruleRepository.saveAll(existingRules);
    }

    int nextSortOrder = allRules.isEmpty() ? 0 : allRules.get(allRules.size() - 1).getSortOrder() + 1;
    List<Rule> replacementRules = new ArrayList<>();
    for (String fragment : fragments) {
      Rule rule = new Rule();
      rule.setUser(user.get());
      rule.setName(uniqueRuleName(category.get()));
      rule.setMatchText(fragment);
      rule.setMatchField(com.example.finanzapp.domain.RuleMatchField.BOTH);
      rule.setCategory(category.get());
      rule.setActive(active);
      rule.setSortOrder(nextSortOrder++);
      replacementRules.add(rule);
    }

    try {
      ruleRepository.saveAll(replacementRules);
      if (existingRules.isEmpty()) {
        normalizeSortOrder(user.get());
      } else {
        reindexByCategoryOrder(user.get(), categoryOrder);
      }
      return RuleGroupUpdateStatus.SUCCESS;
    } catch (DataIntegrityViolationException ignored) {
      return RuleGroupUpdateStatus.PERSISTENCE_ERROR;
    }
  }

  private boolean moveRuleCategory(UserDetails userDetails, Integer categoryId, int delta) {
    if (categoryId == null) {
      return false;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return false;
    }

    List<Rule> orderedRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    List<Integer> categoryOrder = buildCategoryOrder(orderedRules);
    int index = categoryOrder.indexOf(categoryId);
    if (index < 0) {
      return false;
    }

    int targetIndex = index + delta;
    if (targetIndex < 0 || targetIndex >= categoryOrder.size()) {
      return false;
    }

    Collections.swap(categoryOrder, index, targetIndex);
    reindexByCategoryOrder(user.get(), categoryOrder);
    return true;
  }

  private void normalizeSortOrder(User user) {
    List<Rule> rules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user);
    for (int i = 0; i < rules.size(); i++) {
      rules.get(i).setSortOrder(i);
    }
    ruleRepository.saveAll(rules);
  }

  private void reindexByCategoryOrder(User user, List<Integer> preferredCategoryOrder) {
    List<Rule> rules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user);
    Map<Integer, List<Rule>> grouped = groupRulesByCategory(rules);
    if (grouped.isEmpty()) {
      return;
    }

    List<Integer> categoryOrder = new ArrayList<>();
    if (preferredCategoryOrder != null) {
      for (Integer categoryId : preferredCategoryOrder) {
        if (categoryId != null && grouped.containsKey(categoryId) && !categoryOrder.contains(categoryId)) {
          categoryOrder.add(categoryId);
        }
      }
    }
    for (Integer categoryId : grouped.keySet()) {
      if (!categoryOrder.contains(categoryId)) {
        categoryOrder.add(categoryId);
      }
    }

    List<Rule> reordered = new ArrayList<>();
    for (Integer categoryId : categoryOrder) {
      reordered.addAll(grouped.get(categoryId));
    }

    for (int i = 0; i < reordered.size(); i++) {
      reordered.get(i).setSortOrder(i);
    }
    ruleRepository.saveAll(reordered);
  }

  private Map<Integer, List<Rule>> groupRulesByCategory(List<Rule> orderedRules) {
    Map<Integer, List<Rule>> grouped = new LinkedHashMap<>();
    for (Rule rule : orderedRules) {
      if (rule.getCategory() == null || rule.getCategory().getId() == null) {
        continue;
      }
      Integer categoryId = rule.getCategory().getId();
      grouped.computeIfAbsent(categoryId, key -> new ArrayList<>()).add(rule);
    }
    return grouped;
  }

  private List<Integer> buildCategoryOrder(List<Rule> orderedRules) {
    List<Integer> order = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (Rule rule : orderedRules) {
      if (rule.getCategory() == null || rule.getCategory().getId() == null) {
        continue;
      }
      Integer categoryId = rule.getCategory().getId();
      if (seen.add(categoryId)) {
        order.add(categoryId);
      }
    }
    return order;
  }

  private RuleGroupPreparedData prepareRuleGroupData(User user, RuleGroupCommand command) {
    if (command == null || command.categoryId() == null) {
      return null;
    }

    Optional<Category> category = resolveSubCategory(user, command.categoryId());
    if (category.isEmpty()) {
      return null;
    }

    List<String> fragments = parseFragments(command.fragmentsText());
    if (fragments.isEmpty()) {
      return null;
    }

    return new RuleGroupPreparedData(category.get(), fragments);
  }

  private List<String> parseFragments(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }

    List<String> fragments = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (String line : raw.split("\\R")) {
      for (String part : line.split("[,;]")) {
        String candidate = part == null ? "" : part.trim();
        if (candidate.isBlank()) {
          continue;
        }
        String dedupeKey = candidate.toLowerCase(Locale.ROOT);
        if (seen.add(dedupeKey)) {
          fragments.add(candidate);
        }
      }
    }

    return List.copyOf(fragments);
  }

  private Optional<Category> resolveSubCategory(User user, Integer categoryId) {
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

  private String resolveCategoryLabel(Category category) {
    if (category == null) {
      return "-";
    }
    if (category.getParent() == null) {
      return category.getName();
    }
    return category.getParent().getName() + " -> " + category.getName();
  }

  private String uniqueRuleName(Category category) {
    Integer categoryId = category == null ? null : category.getId();
    if (categoryId == null) {
      return "rule-" + UUID.randomUUID();
    }
    return "rule-" + categoryId + "-" + UUID.randomUUID();
  }

  private record RuleGroupPreparedData(Category category, List<String> fragments) {}

  public record RuleGroupCommand(Integer categoryId, String fragmentsText) {}

  public record RuleCategoryRow(
      Integer categoryId,
      String categoryLabel,
      List<String> fragments,
      int fragmentCount,
      boolean active,
      String lastRunLabel,
      int lastMatchCount,
      boolean canMoveUp,
      boolean canMoveDown) {}

  public record RuleCategoryOption(Integer id, String label) {}

  public record RuleGroupFormData(Integer categoryId, String categoryLabel, String fragmentsText) {}

  public enum RuleGroupUpdateStatus {
    SUCCESS,
    USER_NOT_FOUND,
    CATEGORY_NOT_FOUND,
    INVALID_FRAGMENTS,
    PERSISTENCE_ERROR
  }
}
