package com.example.finanzapp.categories;

import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.Rule;
import com.example.finanzapp.domain.RuleMatchField;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.CategoryRepository;
import com.example.finanzapp.repository.RuleRepository;
import com.example.finanzapp.repository.UserRepository;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CategoryTransferService {
  private static final String EXPORT_FORMAT = "finanztool-categories-v1";
  private static final int MAX_NAME_LENGTH = 80;

  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final RuleRepository ruleRepository;
  private final CategoryBootstrapService categoryBootstrapService;
  private final ObjectMapper objectMapper;

  public CategoryTransferService(
      UserRepository userRepository,
      CategoryRepository categoryRepository,
      RuleRepository ruleRepository,
      CategoryBootstrapService categoryBootstrapService,
      ObjectMapper objectMapper) {
    this.userRepository = userRepository;
    this.categoryRepository = categoryRepository;
    this.ruleRepository = ruleRepository;
    this.categoryBootstrapService = categoryBootstrapService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public Optional<String> exportAsJson(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return Optional.empty();
    }

    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user.get());
    Map<Integer, List<Rule>> rulesByCategoryId = loadRulesByCategoryId(user.get());
    List<ExportParent> exportParents = new ArrayList<>();
    List<ExportRuleGroup> exportRuleGroups = new ArrayList<>();
    for (Category parent : parents) {
      String parentName = normalizeName(parent.getName());
      if (parentName == null) {
        continue;
      }

      List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), parent);
      List<String> subcategories = new ArrayList<>();
      for (Category child : children) {
        String subName = normalizeName(child.getName());
        if (subName == null) {
          continue;
        }
        subcategories.add(subName);

        List<Rule> categoryRules = child.getId() == null
            ? List.of()
            : rulesByCategoryId.getOrDefault(child.getId(), List.of());
        ExportRuleGroup ruleGroup = toExportRuleGroup(parentName, subName, categoryRules);
        if (ruleGroup != null) {
          exportRuleGroups.add(ruleGroup);
        }
      }
      exportParents.add(new ExportParent(parentName, List.copyOf(subcategories)));
    }

    ExportPayload payload = new ExportPayload(
        EXPORT_FORMAT,
        Instant.now(),
        List.copyOf(exportParents),
        List.copyOf(exportRuleGroups));
    try {
      return Optional.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize categories export", ex);
    }
  }

  @Transactional
  public ImportResult importFromJson(UserDetails userDetails, MultipartFile file) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return new ImportResult(ImportStatus.USER_NOT_FOUND, 0, 0);
    }

    if (file == null || file.isEmpty()) {
      return new ImportResult(ImportStatus.EMPTY_FILE, 0, 0);
    }

    ImportPayload payload;
    try {
      payload = objectMapper.readValue(file.getBytes(), ImportPayload.class);
    } catch (IOException ex) {
      return new ImportResult(ImportStatus.INVALID_JSON, 0, 0);
    }

    if (payload == null || payload.format() == null || !EXPORT_FORMAT.equals(payload.format().trim())) {
      return new ImportResult(ImportStatus.INVALID_FORMAT, 0, 0);
    }

    List<PreparedParent> preparedParents = prepareParents(payload.parents());
    if (preparedParents == null) {
      return new ImportResult(ImportStatus.INVALID_CATEGORIES, 0, 0);
    }

    List<Category> existingParents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user.get());
    Map<String, Category> parentsByName = new LinkedHashMap<>();
    for (Category parent : existingParents) {
      String key = normalizeKey(parent.getName());
      if (!key.isBlank()) {
        parentsByName.putIfAbsent(key, parent);
      }
    }

    int nextParentSortOrder = existingParents.stream()
        .mapToInt(Category::getSortOrder)
        .max()
        .orElse(-1) + 1;

    int createdParents = 0;
    int createdSubcategories = 0;

    for (PreparedParent preparedParent : preparedParents) {
      String parentKey = normalizeKey(preparedParent.name());
      Category parent = parentsByName.get(parentKey);
      if (parent == null) {
        Category createdParent = new Category();
        createdParent.setUser(user.get());
        createdParent.setName(preparedParent.name());
        createdParent.setSortOrder(nextParentSortOrder++);
        createdParent.setDefault(false);
        createdParent.setSystem(false);
        parent = categoryRepository.save(createdParent);
        parentsByName.put(parentKey, parent);
        createdParents++;
      }

      List<Category> existingSubcategories = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), parent);
      Map<String, Category> subcategoriesByName = new LinkedHashMap<>();
      for (Category existingSub : existingSubcategories) {
        String key = normalizeKey(existingSub.getName());
        if (!key.isBlank()) {
          subcategoriesByName.putIfAbsent(key, existingSub);
        }
      }

      int nextSubSortOrder = existingSubcategories.stream()
          .mapToInt(Category::getSortOrder)
          .max()
          .orElse(-1) + 1;

      for (String subcategoryName : preparedParent.subcategories()) {
        String subKey = normalizeKey(subcategoryName);
        if (subcategoriesByName.containsKey(subKey)) {
          continue;
        }

        Category createdSubcategory = new Category();
        createdSubcategory.setUser(user.get());
        createdSubcategory.setParent(parent);
        createdSubcategory.setName(subcategoryName);
        createdSubcategory.setSortOrder(nextSubSortOrder++);
        createdSubcategory.setDefault(false);
        createdSubcategory.setSystem(false);
        categoryRepository.save(createdSubcategory);
        subcategoriesByName.put(subKey, createdSubcategory);
        createdSubcategories++;
      }
    }

    return new ImportResult(ImportStatus.SUCCESS, createdParents, createdSubcategories);
  }

  private List<PreparedParent> prepareParents(List<ImportParent> importedParents) {
    List<ImportParent> source = importedParents == null ? List.of() : importedParents;
    Map<String, MutableParent> mergedParents = new LinkedHashMap<>();

    for (ImportParent importParent : source) {
      if (importParent == null) {
        return null;
      }

      String parentName = normalizeName(importParent.name());
      if (parentName == null) {
        return null;
      }

      String parentKey = normalizeKey(parentName);
      MutableParent mutableParent = mergedParents.computeIfAbsent(parentKey, key -> new MutableParent(parentName));
      List<String> importedSubcategories = importParent.subcategories() == null ? List.of() : importParent.subcategories();
      for (String importedSubcategory : importedSubcategories) {
        String subcategoryName = normalizeName(importedSubcategory);
        if (subcategoryName == null) {
          return null;
        }
        mutableParent.addSubcategory(subcategoryName);
      }
    }

    List<PreparedParent> preparedParents = new ArrayList<>();
    for (MutableParent mergedParent : mergedParents.values()) {
      preparedParents.add(new PreparedParent(mergedParent.name(), List.copyOf(mergedParent.subcategories())));
    }
    return List.copyOf(preparedParents);
  }

  private String normalizeName(String rawName) {
    if (rawName == null) {
      return null;
    }
    String normalized = rawName.trim();
    if (normalized.isBlank() || normalized.length() > MAX_NAME_LENGTH) {
      return null;
    }
    return normalized;
  }

  private String normalizeKey(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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

  private Map<Integer, List<Rule>> loadRulesByCategoryId(User user) {
    Map<Integer, List<Rule>> groupedRules = new LinkedHashMap<>();
    for (Rule rule : ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user)) {
      if (rule.getCategory() == null || rule.getCategory().getId() == null) {
        continue;
      }
      groupedRules.computeIfAbsent(rule.getCategory().getId(), key -> new ArrayList<>()).add(rule);
    }
    return groupedRules;
  }

  private ExportRuleGroup toExportRuleGroup(
      String parentName,
      String categoryName,
      List<Rule> categoryRules) {
    if (categoryRules == null || categoryRules.isEmpty()) {
      return null;
    }

    List<String> fragments = categoryRules.stream()
        .map(Rule::getMatchText)
        .map(value -> value == null ? "" : value.trim())
        .filter(value -> !value.isBlank())
        .toList();
    if (fragments.isEmpty()) {
      return null;
    }

    Rule firstRule = categoryRules.get(0);
    RuleMatchField matchField = firstRule.getMatchField() == null ? RuleMatchField.BOTH : firstRule.getMatchField();
    boolean active = categoryRules.stream().allMatch(Rule::isActive);

    return new ExportRuleGroup(parentName, categoryName, matchField.name(), active, fragments);
  }

  private record ExportPayload(
      String format,
      Instant exportedAt,
      List<ExportParent> parents,
      List<ExportRuleGroup> ruleGroups) {}

  private record ExportParent(String name, List<String> subcategories) {}

  private record ExportRuleGroup(
      String parentCategory,
      String category,
      String matchField,
      boolean active,
      List<String> fragments) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ImportPayload(String format, List<ImportParent> parents) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ImportParent(String name, List<String> subcategories) {}

  private record PreparedParent(String name, List<String> subcategories) {}

  private static final class MutableParent {
    private final String name;
    private final List<String> subcategories;
    private final Set<String> subcategoryKeys;

    private MutableParent(String name) {
      this.name = name;
      this.subcategories = new ArrayList<>();
      this.subcategoryKeys = new HashSet<>();
    }

    private String name() {
      return name;
    }

    private List<String> subcategories() {
      return subcategories;
    }

    private void addSubcategory(String subcategory) {
      String key = subcategory == null ? "" : subcategory.trim().toLowerCase(Locale.ROOT);
      if (key.isBlank() || !subcategoryKeys.add(key)) {
        return;
      }
      subcategories.add(subcategory);
    }
  }

  public enum ImportStatus {
    SUCCESS,
    USER_NOT_FOUND,
    EMPTY_FILE,
    INVALID_JSON,
    INVALID_FORMAT,
    INVALID_CATEGORIES
  }

  public record ImportResult(ImportStatus status, int importedParentCount, int importedSubcategoryCount) {}
}
