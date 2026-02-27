package de.kruemelnerd.finanzapp.rules;

import de.kruemelnerd.finanzapp.categories.CategoryBootstrapService;
import de.kruemelnerd.finanzapp.domain.Category;
import de.kruemelnerd.finanzapp.domain.Rule;
import de.kruemelnerd.finanzapp.domain.RuleMatchField;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.CategoryRepository;
import de.kruemelnerd.finanzapp.repository.RuleRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RuleTransferService {
  private static final String EXPORT_FORMAT = "finanztool-rules-v1";

  private final UserRepository userRepository;
  private final RuleRepository ruleRepository;
  private final CategoryRepository categoryRepository;
  private final CategoryBootstrapService categoryBootstrapService;
  private final ObjectMapper objectMapper;

  public RuleTransferService(
      UserRepository userRepository,
      RuleRepository ruleRepository,
      CategoryRepository categoryRepository,
      CategoryBootstrapService categoryBootstrapService,
      ObjectMapper objectMapper) {
    this.userRepository = userRepository;
    this.ruleRepository = ruleRepository;
    this.categoryRepository = categoryRepository;
    this.categoryBootstrapService = categoryBootstrapService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public Optional<String> exportAsJson(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return Optional.empty();
    }

    List<Rule> orderedRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    Map<Integer, List<Rule>> groupedRules = groupByCategory(orderedRules);

    List<ExportRuleGroup> groups = new ArrayList<>();
    for (List<Rule> categoryRules : groupedRules.values()) {
      if (categoryRules.isEmpty()) {
        continue;
      }

      Rule first = categoryRules.get(0);
      Category category = first.getCategory();
      if (category == null || category.getParent() == null) {
        continue;
      }

      String parentName = safeName(category.getParent());
      String categoryName = safeName(category);
      if (parentName.isBlank() || categoryName.isBlank()) {
        continue;
      }

      boolean active = categoryRules.stream().allMatch(Rule::isActive);
      RuleMatchField matchField = first.getMatchField() == null ? RuleMatchField.BOTH : first.getMatchField();
      List<String> fragments = categoryRules.stream()
          .map(Rule::getMatchText)
          .map(value -> value == null ? "" : value.trim())
          .filter(value -> !value.isBlank())
          .toList();
      if (fragments.isEmpty()) {
        continue;
      }

      groups.add(new ExportRuleGroup(parentName, categoryName, matchField.name(), active, fragments));
    }

    ExportPayload payload = new ExportPayload(EXPORT_FORMAT, Instant.now(), groups);
    try {
      return Optional.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize rules export", ex);
    }
  }

  @Transactional
  public ImportResult importFromJson(UserDetails userDetails, MultipartFile file) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return new ImportResult(ImportStatus.USER_NOT_FOUND, 0, 0, null);
    }

    if (file == null || file.isEmpty()) {
      return new ImportResult(ImportStatus.EMPTY_FILE, 0, 0, null);
    }

    ImportPayload payload;
    try {
      payload = objectMapper.readValue(file.getBytes(), ImportPayload.class);
    } catch (IOException ex) {
      return new ImportResult(ImportStatus.INVALID_JSON, 0, 0, null);
    }

    if (payload == null || payload.format() == null || !EXPORT_FORMAT.equals(payload.format().trim())) {
      return new ImportResult(ImportStatus.INVALID_FORMAT, 0, 0, null);
    }

    List<ImportRuleGroup> groups = payload.groups() == null ? List.of() : payload.groups();
    List<PreparedGroup> preparedGroups = new ArrayList<>();
    Map<String, Category> categoriesByPath = loadCategoriesByPath(user.get());

    for (ImportRuleGroup group : groups) {
      PreparedGroup prepared = prepareGroup(group, categoriesByPath);
      if (prepared == null) {
        return new ImportResult(ImportStatus.INVALID_RULES, 0, 0, null);
      }
      if (prepared.missingCategoryLabel() != null) {
        return new ImportResult(ImportStatus.UNKNOWN_CATEGORY, 0, 0, prepared.missingCategoryLabel());
      }
      preparedGroups.add(prepared);
    }

    List<Rule> existingRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get());
    Set<String> existingRuleKeys = new HashSet<>();
    int nextSortOrder = 0;
    for (Rule existingRule : existingRules) {
      if (existingRule.getSortOrder() >= nextSortOrder) {
        nextSortOrder = existingRule.getSortOrder() + 1;
      }
      existingRuleKeys.add(ruleKey(existingRule));
    }

    int importedGroupCount = 0;
    List<Rule> importedRules = new ArrayList<>();
    for (PreparedGroup prepared : preparedGroups) {
      boolean groupImported = false;
      for (String fragment : prepared.fragments()) {
        String key = ruleKey(prepared.category().getId(), prepared.matchField(), fragment);
        if (existingRuleKeys.contains(key)) {
          continue;
        }

        Rule rule = new Rule();
        rule.setUser(user.get());
        rule.setName(uniqueRuleName(prepared.category()));
        rule.setMatchText(fragment);
        rule.setMatchField(prepared.matchField());
        rule.setCategory(prepared.category());
        rule.setActive(prepared.active());
        rule.setSortOrder(nextSortOrder++);
        importedRules.add(rule);
        existingRuleKeys.add(key);
        groupImported = true;
      }
      if (groupImported) {
        importedGroupCount++;
      }
    }

    if (!importedRules.isEmpty()) {
      ruleRepository.saveAll(importedRules);
    }

    return new ImportResult(ImportStatus.SUCCESS, importedGroupCount, importedRules.size(), null);
  }

  private PreparedGroup prepareGroup(ImportRuleGroup group, Map<String, Category> categoriesByPath) {
    if (group == null) {
      return null;
    }

    String parentName = group.parentCategory() == null ? "" : group.parentCategory().trim();
    String categoryName = group.category() == null ? "" : group.category().trim();
    if (parentName.isBlank() || categoryName.isBlank()) {
      return null;
    }

    String key = categoryKey(parentName, categoryName);
    Category category = categoriesByPath.get(key);
    if (category == null) {
      return PreparedGroup.missingCategory(parentName + " -> " + categoryName);
    }

    RuleMatchField matchField = parseMatchField(group.matchField());
    if (matchField == null) {
      return null;
    }

    List<String> fragments = normalizeFragments(group.fragments());
    if (fragments.isEmpty()) {
      return null;
    }

    boolean active = group.active() == null || group.active();
    return PreparedGroup.ready(category, matchField, active, fragments);
  }

  private List<String> normalizeFragments(List<String> rawFragments) {
    if (rawFragments == null) {
      return List.of();
    }
    List<String> fragments = new ArrayList<>();
    for (String raw : rawFragments) {
      String normalized = raw == null ? "" : raw.trim();
      if (!normalized.isBlank()) {
        fragments.add(normalized);
      }
    }
    return List.copyOf(fragments);
  }

  private RuleMatchField parseMatchField(String rawMatchField) {
    if (rawMatchField == null || rawMatchField.isBlank()) {
      return RuleMatchField.BOTH;
    }
    try {
      return RuleMatchField.valueOf(rawMatchField.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private Map<String, Category> loadCategoriesByPath(User user) {
    List<Category> subCategories = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNotNullOrderBySortOrderAscIdAsc(user);
    Map<String, Category> categoriesByPath = new LinkedHashMap<>();
    for (Category category : subCategories) {
      if (category.getParent() == null) {
        continue;
      }
      String parentName = safeName(category.getParent());
      String categoryName = safeName(category);
      if (parentName.isBlank() || categoryName.isBlank()) {
        continue;
      }
      categoriesByPath.put(categoryKey(parentName, categoryName), category);
    }
    return categoriesByPath;
  }

  private Map<Integer, List<Rule>> groupByCategory(List<Rule> orderedRules) {
    Map<Integer, List<Rule>> grouped = new LinkedHashMap<>();
    for (Rule rule : orderedRules) {
      if (rule.getCategory() == null || rule.getCategory().getId() == null) {
        continue;
      }
      grouped.computeIfAbsent(rule.getCategory().getId(), key -> new ArrayList<>()).add(rule);
    }
    return grouped;
  }

  private String categoryKey(String parentName, String categoryName) {
    return normalize(parentName) + "|" + normalize(categoryName);
  }

  private String ruleKey(Rule rule) {
    if (rule.getCategory() == null || rule.getCategory().getId() == null) {
      return "";
    }
    return ruleKey(rule.getCategory().getId(), rule.getMatchField(), rule.getMatchText());
  }

  private String ruleKey(Integer categoryId, RuleMatchField matchField, String fragment) {
    String field = matchField == null ? RuleMatchField.BOTH.name() : matchField.name();
    return categoryId + "|" + field + "|" + normalize(fragment);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String safeName(Category category) {
    return category.getName() == null ? "" : category.getName().trim();
  }

  private String uniqueRuleName(Category category) {
    Integer categoryId = category == null ? null : category.getId();
    if (categoryId == null) {
      return "rule-" + UUID.randomUUID();
    }
    return "rule-" + categoryId + "-" + UUID.randomUUID();
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

  private record ExportPayload(String format, Instant exportedAt, List<ExportRuleGroup> groups) {}

  private record ExportRuleGroup(
      String parentCategory,
      String category,
      String matchField,
      boolean active,
      List<String> fragments) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ImportPayload(String format, List<ImportRuleGroup> groups) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ImportRuleGroup(
      String parentCategory,
      String category,
      String matchField,
      Boolean active,
      List<String> fragments) {}

  private record PreparedGroup(
      Category category,
      RuleMatchField matchField,
      boolean active,
      List<String> fragments,
      String missingCategoryLabel) {

    private static PreparedGroup ready(Category category, RuleMatchField matchField, boolean active, List<String> fragments) {
      return new PreparedGroup(category, matchField, active, fragments, null);
    }

    private static PreparedGroup missingCategory(String label) {
      return new PreparedGroup(null, null, true, List.of(), label);
    }
  }

  public enum ImportStatus {
    SUCCESS,
    USER_NOT_FOUND,
    EMPTY_FILE,
    INVALID_JSON,
    INVALID_FORMAT,
    INVALID_RULES,
    UNKNOWN_CATEGORY
  }

  public record ImportResult(ImportStatus status, int importedGroupCount, int importedRuleCount, String detail) {}
}
