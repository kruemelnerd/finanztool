package de.kruemelnerd.finanzapp.categories;

import de.kruemelnerd.finanzapp.domain.Category;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.CategoryRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryBootstrapService {
  private static final String FALLBACK_PARENT = "Sonstiges";
  private static final String FALLBACK_SUB = "Unkategorisiert";

  private static final List<ParentSeed> DEFAULT_SET = List.of(
      new ParentSeed("Einnahmen", List.of("Gehalt", "Erstattung", "Sonstige Einnahmen")),
      new ParentSeed("Wohnen", List.of("Miete/Hypothek", "Nebenkosten", "Internet/Telefon")),
      new ParentSeed("Essen & Trinken", List.of("Groceries", "Restaurants", "FastFood", "Coffee/Snacks")),
      new ParentSeed("Transport", List.of("OePNV", "Tanken", "Bahn/Flug", "Parkplatz/Maut")),
      new ParentSeed("Rechnungen & Abos", List.of("Streaming", "Mobilfunk", "Strom/Gas/Wasser")),
      new ParentSeed("Shopping", List.of("Kleidung", "Elektronik", "Haushalt", "Sport")),
      new ParentSeed("Gesundheit", List.of("Apotheke", "Arzt", "Fitness")),
      new ParentSeed("Freizeit", List.of("Entertainment", "Urlaub", "Hobbys")),
      new ParentSeed("Versicherung", List.of("Haftpflicht", "KFZ", "Sonstige Versicherungen")),
      new ParentSeed("Finanzen", List.of("Gebuehren", "Steuern", "Sparen/Investieren")),
      new ParentSeed(FALLBACK_PARENT, List.of(FALLBACK_SUB))
  );

  private final CategoryRepository categoryRepository;

  public CategoryBootstrapService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  @Transactional
  public Category ensureDefaultUncategorized(User user) {
    Optional<Category> existingFallback = categoryRepository.findByUserAndIsDefaultTrueAndDeletedAtIsNull(user);
    if (existingFallback.isPresent()) {
      return existingFallback.get();
    }

    if (!categoryRepository.existsByUserAndDeletedAtIsNull(user)) {
      seedDefaultSet(user);
      return categoryRepository.findByUserAndIsDefaultTrueAndDeletedAtIsNull(user)
          .orElseThrow(() -> new IllegalStateException("Missing fallback category after bootstrap"));
    }

    return ensureFallbackCategory(user);
  }

  private void seedDefaultSet(User user) {
    Map<String, Category> parents = new LinkedHashMap<>();

    int parentSortOrder = 0;
    for (ParentSeed seed : DEFAULT_SET) {
      Category parent = saveCategory(user, null, seed.parentName(), parentSortOrder++, false, false);
      parents.put(seed.parentName(), parent);
    }

    for (ParentSeed seed : DEFAULT_SET) {
      Category parent = parents.get(seed.parentName());
      int subSortOrder = 0;
      for (String subName : seed.subNames()) {
        boolean isFallback = FALLBACK_PARENT.equals(seed.parentName()) && FALLBACK_SUB.equals(subName);
        saveCategory(user, parent, subName, subSortOrder++, isFallback, isFallback);
      }
    }
  }

  private Category ensureFallbackCategory(User user) {
    Category parent = categoryRepository.findByUserAndParentIsNullAndNameIgnoreCaseAndDeletedAtIsNull(user, FALLBACK_PARENT)
        .orElseGet(() -> saveCategory(
            user,
            null,
            FALLBACK_PARENT,
            categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user).size(),
            false,
            false));

    return categoryRepository.findByUserAndParentAndNameIgnoreCaseAndDeletedAtIsNull(user, parent, FALLBACK_SUB)
        .orElseGet(() -> saveCategory(user, parent, FALLBACK_SUB, 0, true, true));
  }

  private Category saveCategory(
      User user,
      Category parent,
      String name,
      int sortOrder,
      boolean isDefault,
      boolean isSystem) {
    Category category = new Category();
    category.setUser(user);
    category.setParent(parent);
    category.setName(name);
    category.setSortOrder(sortOrder);
    category.setDefault(isDefault);
    category.setSystem(isSystem);
    return categoryRepository.save(category);
  }

  private record ParentSeed(String parentName, List<String> subNames) {}
}
