package com.example.finanzapp.categories;

import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.CategoryRepository;
import com.example.finanzapp.repository.RuleRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryManagementService {
  private static final int MAX_NAME_LENGTH = 80;

  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final RuleRepository ruleRepository;
  private final TransactionRepository transactionRepository;
  private final CategoryBootstrapService categoryBootstrapService;

  public CategoryManagementService(
      UserRepository userRepository,
      CategoryRepository categoryRepository,
      RuleRepository ruleRepository,
      TransactionRepository transactionRepository,
      CategoryBootstrapService categoryBootstrapService) {
    this.userRepository = userRepository;
    this.categoryRepository = categoryRepository;
    this.ruleRepository = ruleRepository;
    this.transactionRepository = transactionRepository;
    this.categoryBootstrapService = categoryBootstrapService;
  }

  @Transactional(readOnly = true)
  public CategoryPageData loadCategoryPage(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return new CategoryPageData(List.of(), List.of(), List.of());
    }

    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user.get());
    if (parents.isEmpty()) {
      return new CategoryPageData(List.of(), List.of(), List.of());
    }

    List<ParentRow> parentRows = new ArrayList<>();
    List<SubcategoryRow> subcategoryRows = new ArrayList<>();
    List<ParentOption> parentOptions = new ArrayList<>();

    for (int parentIndex = 0; parentIndex < parents.size(); parentIndex++) {
      Category parent = parents.get(parentIndex);
      if (parent.getId() == null) {
        continue;
      }

      parentRows.add(new ParentRow(
          parent.getId(),
          parent.getName(),
          parent.isSystem(),
          parentIndex > 0,
          parentIndex < (parents.size() - 1)));
      parentOptions.add(new ParentOption(parent.getId(), parent.getName()));

      List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), parent);
      for (int childIndex = 0; childIndex < children.size(); childIndex++) {
        Category child = children.get(childIndex);
        if (child.getId() == null) {
          continue;
        }

        subcategoryRows.add(new SubcategoryRow(
            child.getId(),
            child.getName(),
            parent.getId(),
            parent.getName(),
            child.isDefault(),
            child.isSystem(),
            childIndex > 0,
            childIndex < (children.size() - 1)));
      }
    }

    return new CategoryPageData(
        List.copyOf(parentRows),
        List.copyOf(subcategoryRows),
        List.copyOf(parentOptions));
  }

  @Transactional
  public UpdateStatus createParent(UserDetails userDetails, String rawName) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    String name = normalizeName(rawName);
    if (name == null) {
      return UpdateStatus.INVALID_NAME;
    }

    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user.get());

    Category category = new Category();
    category.setUser(user.get());
    category.setName(name);
    category.setSortOrder(parents.size());
    category.setDefault(false);
    category.setSystem(false);

    try {
      categoryRepository.save(category);
      return UpdateStatus.SUCCESS;
    } catch (DataIntegrityViolationException ignored) {
      return UpdateStatus.DUPLICATE_NAME;
    }
  }

  @Transactional
  public UpdateStatus updateParent(UserDetails userDetails, Integer categoryId, String rawName) {
    if (categoryId == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    Optional<Category> category = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user.get());
    if (category.isEmpty() || category.get().getParent() != null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }
    if (category.get().isSystem()) {
      return UpdateStatus.SYSTEM_PROTECTED;
    }

    String name = normalizeName(rawName);
    if (name == null) {
      return UpdateStatus.INVALID_NAME;
    }

    category.get().setName(name);
    try {
      categoryRepository.save(category.get());
      return UpdateStatus.SUCCESS;
    } catch (DataIntegrityViolationException ignored) {
      return UpdateStatus.DUPLICATE_NAME;
    }
  }

  @Transactional
  public UpdateStatus createSubcategory(UserDetails userDetails, Integer parentId, String rawName) {
    if (parentId == null) {
      return UpdateStatus.PARENT_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    Optional<Category> parent = categoryRepository.findByIdAndUserAndDeletedAtIsNull(parentId, user.get());
    if (parent.isEmpty() || parent.get().getParent() != null) {
      return UpdateStatus.PARENT_NOT_FOUND;
    }

    String name = normalizeName(rawName);
    if (name == null) {
      return UpdateStatus.INVALID_NAME;
    }

    List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), parent.get());

    Category category = new Category();
    category.setUser(user.get());
    category.setParent(parent.get());
    category.setName(name);
    category.setSortOrder(children.size());
    category.setDefault(false);
    category.setSystem(false);

    try {
      categoryRepository.save(category);
      return UpdateStatus.SUCCESS;
    } catch (DataIntegrityViolationException ignored) {
      return UpdateStatus.DUPLICATE_NAME;
    }
  }

  @Transactional
  public UpdateStatus updateSubcategory(UserDetails userDetails, Integer categoryId, Integer parentId, String rawName) {
    if (categoryId == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }
    if (parentId == null) {
      return UpdateStatus.PARENT_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    Optional<Category> subcategory = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user.get());
    if (subcategory.isEmpty() || subcategory.get().getParent() == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }
    if (subcategory.get().isSystem()) {
      return UpdateStatus.SYSTEM_PROTECTED;
    }

    Optional<Category> parent = categoryRepository.findByIdAndUserAndDeletedAtIsNull(parentId, user.get());
    if (parent.isEmpty() || parent.get().getParent() != null) {
      return UpdateStatus.PARENT_NOT_FOUND;
    }

    String name = normalizeName(rawName);
    if (name == null) {
      return UpdateStatus.INVALID_NAME;
    }

    Category current = subcategory.get();
    Category previousParent = current.getParent();
    Category nextParent = parent.get();
    boolean parentChanged = !Objects.equals(previousParent.getId(), nextParent.getId());

    if (parentChanged) {
      List<Category> targetChildren = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), nextParent);
      current.setParent(nextParent);
      current.setSortOrder(targetChildren.size());
    }
    current.setName(name);

    try {
      categoryRepository.save(current);
    } catch (DataIntegrityViolationException ignored) {
      return UpdateStatus.DUPLICATE_NAME;
    }

    if (parentChanged) {
      normalizeChildSortOrder(user.get(), previousParent);
      normalizeChildSortOrder(user.get(), nextParent);
    }

    return UpdateStatus.SUCCESS;
  }

  @Transactional
  public UpdateStatus deleteParent(UserDetails userDetails, Integer categoryId) {
    if (categoryId == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    Optional<Category> parent = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user.get());
    if (parent.isEmpty() || parent.get().getParent() != null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }
    if (parent.get().isSystem()) {
      return UpdateStatus.SYSTEM_PROTECTED;
    }

    if (isCategoryInUse(user.get(), parent.get())) {
      return UpdateStatus.CATEGORY_IN_USE;
    }

    List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), parent.get());
    for (Category child : children) {
      if (child.isSystem()) {
        return UpdateStatus.SYSTEM_PROTECTED;
      }
      if (isCategoryInUse(user.get(), child)) {
        return UpdateStatus.CATEGORY_IN_USE;
      }
    }

    Instant now = Instant.now();
    parent.get().setDeletedAt(now);
    for (Category child : children) {
      child.setDeletedAt(now);
    }

    categoryRepository.save(parent.get());
    categoryRepository.saveAll(children);
    normalizeParentSortOrder(user.get());
    return UpdateStatus.SUCCESS;
  }

  @Transactional
  public UpdateStatus deleteSubcategory(UserDetails userDetails, Integer categoryId) {
    if (categoryId == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    Optional<Category> subcategory = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user.get());
    if (subcategory.isEmpty() || subcategory.get().getParent() == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }
    if (subcategory.get().isSystem()) {
      return UpdateStatus.SYSTEM_PROTECTED;
    }
    if (isCategoryInUse(user.get(), subcategory.get())) {
      return UpdateStatus.CATEGORY_IN_USE;
    }

    Category parent = subcategory.get().getParent();
    subcategory.get().setDeletedAt(Instant.now());
    categoryRepository.save(subcategory.get());
    normalizeChildSortOrder(user.get(), parent);
    return UpdateStatus.SUCCESS;
  }

  @Transactional
  public UpdateStatus reorder(UserDetails userDetails, CategoryReorderCommand command) {
    if (command == null || command.parents() == null) {
      return UpdateStatus.INVALID_REORDER;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user.get());
    List<Category> children = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNotNullOrderBySortOrderAscIdAsc(user.get());

    Map<Integer, Category> parentById = new HashMap<>();
    for (Category parent : parents) {
      if (parent.getId() != null) {
        parentById.put(parent.getId(), parent);
      }
    }

    Map<Integer, Category> childById = new HashMap<>();
    for (Category child : children) {
      if (child.getId() != null) {
        childById.put(child.getId(), child);
      }
    }

    if (command.parents().size() != parentById.size()) {
      return UpdateStatus.INVALID_REORDER;
    }

    Set<Integer> parentIds = parentById.keySet();
    Set<Integer> commandParentIds = new HashSet<>();
    Set<Integer> commandChildIds = new HashSet<>();

    for (int index = 0; index < command.parents().size(); index++) {
      ParentOrderCommand parentOrder = command.parents().get(index);
      if (parentOrder == null || parentOrder.parentId() == null || !commandParentIds.add(parentOrder.parentId())) {
        return UpdateStatus.INVALID_REORDER;
      }

      Category parent = parentById.get(parentOrder.parentId());
      if (parent == null) {
        return UpdateStatus.INVALID_REORDER;
      }
      parent.setSortOrder(index);

      List<Integer> subcategoryIds = parentOrder.subcategoryIds() == null ? List.of() : parentOrder.subcategoryIds();
      for (int childIndex = 0; childIndex < subcategoryIds.size(); childIndex++) {
        Integer childId = subcategoryIds.get(childIndex);
        if (childId == null || !commandChildIds.add(childId)) {
          return UpdateStatus.INVALID_REORDER;
        }

        Category child = childById.get(childId);
        if (child == null) {
          return UpdateStatus.INVALID_REORDER;
        }

        child.setParent(parent);
        child.setSortOrder(childIndex);
      }
    }

    if (!commandParentIds.equals(parentIds)) {
      return UpdateStatus.INVALID_REORDER;
    }
    if (!commandChildIds.equals(childById.keySet())) {
      return UpdateStatus.INVALID_REORDER;
    }

    categoryRepository.saveAll(parents);
    categoryRepository.saveAll(children);
    return UpdateStatus.SUCCESS;
  }

  @Transactional
  public UpdateStatus moveParent(UserDetails userDetails, Integer categoryId, int delta) {
    if (categoryId == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user.get());
    int index = indexOfCategory(parents, categoryId);
    if (index < 0) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    int targetIndex = index + delta;
    if (targetIndex < 0 || targetIndex >= parents.size()) {
      return UpdateStatus.CANNOT_MOVE;
    }

    Collections.swap(parents, index, targetIndex);
    normalizeSortOrder(parents);
    categoryRepository.saveAll(parents);
    return UpdateStatus.SUCCESS;
  }

  @Transactional
  public UpdateStatus moveSubcategory(UserDetails userDetails, Integer categoryId, int delta) {
    if (categoryId == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return UpdateStatus.USER_NOT_FOUND;
    }

    Optional<Category> category = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user.get());
    if (category.isEmpty() || category.get().getParent() == null) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    Category parent = category.get().getParent();
    List<Category> siblings = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.get(), parent);
    int index = indexOfCategory(siblings, categoryId);
    if (index < 0) {
      return UpdateStatus.CATEGORY_NOT_FOUND;
    }

    int targetIndex = index + delta;
    if (targetIndex < 0 || targetIndex >= siblings.size()) {
      return UpdateStatus.CANNOT_MOVE;
    }

    Collections.swap(siblings, index, targetIndex);
    normalizeSortOrder(siblings);
    categoryRepository.saveAll(siblings);
    return UpdateStatus.SUCCESS;
  }

  private boolean isCategoryInUse(User user, Category category) {
    if (category.getId() == null) {
      return false;
    }
    long transactions = transactionRepository.countByUserAndCategoryAndDeletedAtIsNull(user, category);
    long rules = ruleRepository.countByUserAndCategoryIdAndDeletedAtIsNull(user, category.getId());
    return transactions > 0 || rules > 0;
  }

  private int indexOfCategory(List<Category> categories, Integer categoryId) {
    for (int i = 0; i < categories.size(); i++) {
      if (Objects.equals(categories.get(i).getId(), categoryId)) {
        return i;
      }
    }
    return -1;
  }

  private void normalizeSortOrder(List<Category> categories) {
    for (int i = 0; i < categories.size(); i++) {
      categories.get(i).setSortOrder(i);
    }
  }

  private void normalizeParentSortOrder(User user) {
    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user);
    normalizeSortOrder(parents);
    categoryRepository.saveAll(parents);
  }

  private void normalizeChildSortOrder(User user, Category parent) {
    List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user, parent);
    normalizeSortOrder(children);
    categoryRepository.saveAll(children);
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

  public enum UpdateStatus {
    SUCCESS,
    USER_NOT_FOUND,
    CATEGORY_NOT_FOUND,
    PARENT_NOT_FOUND,
    INVALID_NAME,
    DUPLICATE_NAME,
    SYSTEM_PROTECTED,
    CATEGORY_IN_USE,
    CANNOT_MOVE,
    INVALID_REORDER
  }

  public record CategoryPageData(
      List<ParentRow> parents,
      List<SubcategoryRow> subcategories,
      List<ParentOption> parentOptions) {}

  public record ParentRow(
      Integer id,
      String name,
      boolean systemCategory,
      boolean canMoveUp,
      boolean canMoveDown) {}

  public record SubcategoryRow(
      Integer id,
      String name,
      Integer parentId,
      String parentName,
      boolean defaultCategory,
      boolean systemCategory,
      boolean canMoveUp,
      boolean canMoveDown) {}

  public record ParentOption(Integer id, String name) {}

  public record CategoryReorderCommand(List<ParentOrderCommand> parents) {}

  public record ParentOrderCommand(Integer parentId, List<Integer> subcategoryIds) {}
}
