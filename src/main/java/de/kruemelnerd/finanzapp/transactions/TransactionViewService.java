package de.kruemelnerd.finanzapp.transactions;

import de.kruemelnerd.finanzapp.domain.Category;
import de.kruemelnerd.finanzapp.domain.CategoryAssignedBy;
import de.kruemelnerd.finanzapp.domain.Rule;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.balance.AccountBalanceService;
import de.kruemelnerd.finanzapp.categories.CategoryBootstrapService;
import de.kruemelnerd.finanzapp.repository.CategoryRepository;
import de.kruemelnerd.finanzapp.repository.RuleRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class TransactionViewService {
  private static final DateTimeFormatter DATE_FORMAT_EN = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATE_FORMAT_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int MAX_PAGE_SIZE = 100;

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final AccountBalanceService accountBalanceService;
  private final CategoryBootstrapService categoryBootstrapService;
  private final CategoryRepository categoryRepository;
  private final RuleRepository ruleRepository;

  public TransactionViewService(
      TransactionRepository transactionRepository,
      UserRepository userRepository,
      AccountBalanceService accountBalanceService,
      CategoryBootstrapService categoryBootstrapService,
      CategoryRepository categoryRepository,
      RuleRepository ruleRepository) {
    this.transactionRepository = transactionRepository;
    this.userRepository = userRepository;
    this.accountBalanceService = accountBalanceService;
    this.categoryBootstrapService = categoryBootstrapService;
    this.categoryRepository = categoryRepository;
    this.ruleRepository = ruleRepository;
  }

  public List<TransactionRow> loadTransactions(
      UserDetails userDetails,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String exactName,
      String exactPurpose) {
    LoadedTransactions loaded = loadFilteredTransactions(
        userDetails,
        minAmount,
        maxAmount,
        exactName,
        exactPurpose,
        false,
        null,
        null);
    return loaded.transactions().stream()
        .map(tx -> toRow(tx, loaded.locale(), loaded.categoryDisplayById(), loaded.ruleNameById()))
        .toList();
  }

  public TransactionPage loadTransactionsPage(
      UserDetails userDetails,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String nameContains,
      String purposeContains,
      Boolean onlyUncategorized,
      Integer categoryId,
      Integer parentCategoryId,
      Integer page,
      Integer pageSize) {
    LoadedTransactions loaded = loadFilteredTransactions(
        userDetails,
        minAmount,
        maxAmount,
        nameContains,
        purposeContains,
        onlyUncategorized != null && onlyUncategorized,
        categoryId,
        parentCategoryId);

    int safePageSize = normalizePageSize(pageSize);
    int totalItems = loaded.transactions().size();
    int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safePageSize));
    int safePage = normalizePage(page, totalPages);

    int fromIndex = safePage * safePageSize;
    int toIndex = Math.min(fromIndex + safePageSize, totalItems);
    List<TransactionRow> rows = loaded.transactions().subList(fromIndex, toIndex).stream()
        .map(tx -> toRow(tx, loaded.locale(), loaded.categoryDisplayById(), loaded.ruleNameById()))
        .toList();

    CategoryFilter categoryFilter = loaded.categoryFilter();
    return new TransactionPage(
        rows,
        safePage,
        safePageSize,
        totalPages,
        totalItems,
        categoryFilter.categoryId(),
        categoryFilter.parentCategoryId(),
        categoryFilter.label());
  }

  public List<TransactionRow> loadRecent(UserDetails userDetails, int limit) {
    List<TransactionRow> rows = loadTransactions(userDetails, null, null, null, null);
    if (rows.size() <= limit) {
      return rows;
    }
    return rows.subList(0, limit);
  }

  public String loadCurrentBalanceLabel(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return null;
    }
    Locale locale = resolveLocale(user.get());
    return accountBalanceService.computeCurrentBalanceCents(user.get())
        .map(cents -> formatAmount(cents, locale))
        .orElse(null);
  }

  public List<TransactionCategoryOption> loadCategoryOptions(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return List.of();
    }

    return buildCategoryDisplayById(user.get()).entrySet().stream()
        .map(entry -> new TransactionCategoryOption(
            entry.getKey(),
            entry.getValue().label(),
            entry.getValue().defaultCategory()))
        .toList();
  }

  public boolean setManualCategory(UserDetails userDetails, Integer transactionId, Integer categoryId) {
    if (transactionId == null || categoryId == null) {
      return false;
    }

    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return false;
    }

    Optional<Transaction> transaction = transactionRepository.findByIdAndUserAndDeletedAtIsNull(transactionId, user.get());
    Optional<Category> category = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user.get());
    if (transaction.isEmpty() || category.isEmpty()) {
      return false;
    }

    if (category.get().getParent() == null) {
      return false;
    }

    Transaction tx = transaction.get();
    tx.setCategory(category.get());
    tx.setCategoryAssignedBy(CategoryAssignedBy.MANUAL);
    tx.setCategoryLocked(true);
    tx.setRuleConflicts(null);
    transactionRepository.save(tx);
    return true;
  }

  public boolean softDeleteTransaction(UserDetails userDetails, Integer id) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty() || id == null) {
      return false;
    }
    int updated = transactionRepository.softDeleteByIdAndUser(id, user.get(), Instant.now());
    return updated > 0;
  }

  private TransactionRow toRow(
      Transaction transaction,
      Locale locale,
      Map<Integer, CategoryDisplay> categoryDisplayById,
      Map<Integer, String> ruleNameById) {
    String date = resolveDateFormatter(locale).format(transaction.getBookingDateTime());
    String time = TIME_FORMAT.format(transaction.getBookingDateTime());
    String name = sanitizeNameForDisplay(transaction.getPartnerName());
    String purpose = sanitizePurposeForDisplay(transaction.getPurposeText());
    String amount = formatAmount(transaction.getAmountCents(), locale);

    Integer categoryId = transaction.getCategory() == null ? null : transaction.getCategory().getId();
    CategoryDisplay categoryDisplay = categoryId == null ? null : categoryDisplayById.get(categoryId);
    String conflictNames = resolveConflictNames(transaction.getRuleConflicts(), ruleNameById);

    return new TransactionRow(
        transaction.getId(),
        name,
        purpose,
        date,
        time,
        transaction.getStatus(),
        amount,
        categoryId,
        categoryDisplay == null ? null : categoryDisplay.label(),
        categoryDisplay != null && categoryDisplay.defaultCategory(),
        transaction.getCategoryAssignedBy() == null ? null : transaction.getCategoryAssignedBy().name(),
        transaction.isCategoryLocked(),
        conflictNames);
  }

  private String sanitizeNameForDisplay(String partnerName) {
    if (partnerName == null) {
      return "";
    }
    String value = partnerName.trim();
    if (value.isEmpty()) {
      return "";
    }
    int bookingTextIndex = indexOfIgnoreCase(value, "Buchungstext:");
    if (bookingTextIndex >= 0) {
      value = value.substring(0, bookingTextIndex).trim();
    }
    int cardIndex = indexOfIgnoreCase(value, "Karte Nr.");
    if (cardIndex >= 0) {
      value = value.substring(0, cardIndex).trim();
    }
    int referenceIndex = indexOfIgnoreCase(value, "Ref.");
    if (referenceIndex >= 0) {
      value = value.substring(0, referenceIndex).trim();
    }
    return value;
  }

  private String sanitizePurposeForDisplay(String purposeText) {
    if (purposeText == null) {
      return "";
    }
    String value = purposeText.trim();
    if (value.isEmpty()) {
      return "";
    }
    int bookingTextIndex = indexOfIgnoreCase(value, "Buchungstext:");
    if (bookingTextIndex >= 0) {
      value = value.substring(bookingTextIndex + "Buchungstext:".length()).trim();
    }
    int cardIndex = indexOfIgnoreCase(value, "Karte Nr.");
    if (cardIndex >= 0) {
      value = value.substring(0, cardIndex).trim();
    }
    return value;
  }

  private int indexOfIgnoreCase(String source, String marker) {
    return source.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
  }

  private long toCents(BigDecimal amount) {
    return amount.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
  }

  private String formatAmount(long cents, Locale locale) {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
    DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
    format.setGroupingUsed(true);
    return format.format(cents / 100.0d) + " EUR";
  }

  private DateTimeFormatter resolveDateFormatter(Locale locale) {
    if (Locale.GERMAN.getLanguage().equals(locale.getLanguage())) {
      return DATE_FORMAT_DE;
    }
    return DATE_FORMAT_EN;
  }

  private Locale resolveLocale(User user) {
    if ("DE".equalsIgnoreCase(user.getLanguage())) {
      return Locale.GERMANY;
    }
    return Locale.ENGLISH;
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

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private boolean containsIgnoreCase(String source, String normalizedNeedle) {
    if (source == null) {
      return false;
    }
    return source.toLowerCase(Locale.ROOT).contains(normalizedNeedle);
  }

  private LoadedTransactions loadFilteredTransactions(
      UserDetails userDetails,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String nameContains,
      String purposeContains,
      boolean onlyUncategorized,
      Integer categoryId,
      Integer parentCategoryId) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return new LoadedTransactions(List.of(), Locale.ENGLISH, Map.of(), Map.of(), CategoryFilter.empty());
    }

    List<Transaction> transactions =
        transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user.get());
    Locale locale = resolveLocale(user.get());
    Map<Integer, CategoryDisplay> categoryDisplayById = buildCategoryDisplayById(user.get());
    CategoryFilter categoryFilter = resolveCategoryFilter(user.get(), categoryDisplayById, categoryId, parentCategoryId);

    if (minAmount != null) {
      long minCents = Math.abs(toCents(minAmount));
      transactions = transactions.stream()
          .filter(tx -> Math.abs(tx.getAmountCents()) >= minCents)
          .toList();
    }
    if (maxAmount != null) {
      long maxCents = Math.abs(toCents(maxAmount));
      transactions = transactions.stream()
          .filter(tx -> Math.abs(tx.getAmountCents()) <= maxCents)
          .toList();
    }
    if (hasText(nameContains)) {
      String normalized = nameContains.trim().toLowerCase(Locale.ROOT);
      transactions = transactions.stream()
          .filter(tx -> containsIgnoreCase(tx.getPartnerName(), normalized))
          .toList();
    }
    if (hasText(purposeContains)) {
      String normalized = purposeContains.trim().toLowerCase(Locale.ROOT);
      transactions = transactions.stream()
          .filter(tx -> containsIgnoreCase(tx.getPurposeText(), normalized))
          .toList();
    }

    if (onlyUncategorized) {
      transactions = transactions.stream()
          .filter(tx -> isDefaultCategory(tx, categoryDisplayById))
          .toList();
    }

    if (categoryFilter.categoryId() != null) {
      Integer effectiveCategoryId = categoryFilter.categoryId();
      transactions = transactions.stream()
          .filter(tx -> tx.getCategory() != null && Objects.equals(tx.getCategory().getId(), effectiveCategoryId))
          .toList();
    } else if (categoryFilter.parentCategoryId() != null) {
      transactions = transactions.stream()
          .filter(tx -> isInParentCategory(tx, categoryFilter.subcategoryIds()))
          .toList();
    }

    Map<Integer, String> ruleNameById = loadRuleNameById(user.get(), transactions);
    return new LoadedTransactions(transactions, locale, categoryDisplayById, ruleNameById, categoryFilter);
  }

  private boolean isInParentCategory(Transaction transaction, Set<Integer> subcategoryIds) {
    if (transaction.getCategory() == null || transaction.getCategory().getId() == null) {
      return false;
    }
    return subcategoryIds.contains(transaction.getCategory().getId());
  }

  private CategoryFilter resolveCategoryFilter(
      User user,
      Map<Integer, CategoryDisplay> categoryDisplayById,
      Integer categoryId,
      Integer parentCategoryId) {
    if (categoryId != null) {
      Optional<Category> category = categoryRepository.findByIdAndUserAndDeletedAtIsNull(categoryId, user);
      if (category.isPresent() && category.get().getParent() != null && category.get().getId() != null) {
        Category current = category.get();
        CategoryDisplay display = categoryDisplayById.get(current.getId());
        String label = display == null ? current.getName() : display.label();
        return new CategoryFilter(current.getId(), null, Set.of(current.getId()), label);
      }
    }

    if (parentCategoryId != null) {
      Optional<Category> parent = categoryRepository.findByIdAndUserAndDeletedAtIsNull(parentCategoryId, user);
      if (parent.isPresent() && parent.get().getParent() == null && parent.get().getId() != null) {
        List<Category> subcategories = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user, parent.get());
        Set<Integer> subcategoryIds = subcategories.stream()
            .map(Category::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new CategoryFilter(null, parent.get().getId(), Set.copyOf(subcategoryIds), parent.get().getName());
      }
    }

    return CategoryFilter.empty();
  }

  private boolean isDefaultCategory(Transaction transaction, Map<Integer, CategoryDisplay> categoryDisplayById) {
    if (transaction.getCategoryAssignedBy() == CategoryAssignedBy.DEFAULT) {
      return true;
    }
    if (transaction.getCategory() == null || transaction.getCategory().getId() == null) {
      return false;
    }
    CategoryDisplay display = categoryDisplayById.get(transaction.getCategory().getId());
    return display != null && display.defaultCategory();
  }

  private Map<Integer, CategoryDisplay> buildCategoryDisplayById(User user) {
    List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(user);
    if (parents.isEmpty()) {
      return Map.of();
    }

    Map<Integer, CategoryDisplay> displayById = new HashMap<>();
    for (Category parent : parents) {
      List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user, parent);
      for (Category child : children) {
        if (child.getId() == null) {
          continue;
        }
        String label = parent.getName() + " -> " + child.getName();
        displayById.put(child.getId(), new CategoryDisplay(label, child.isDefault()));
      }
    }
    return displayById;
  }

  private Map<Integer, String> loadRuleNameById(User user, List<Transaction> transactions) {
    Set<Integer> ruleIds = new HashSet<>();
    for (Transaction transaction : transactions) {
      ruleIds.addAll(parseConflictIds(transaction.getRuleConflicts()));
    }
    if (ruleIds.isEmpty()) {
      return Map.of();
    }

    List<Rule> rules = ruleRepository.findByUserAndIdInAndDeletedAtIsNull(user, List.copyOf(ruleIds));
    Map<Integer, String> names = new HashMap<>();
    for (Rule rule : rules) {
      if (rule.getId() != null) {
        names.put(rule.getId(), rule.getName());
      }
    }
    return names;
  }

  private String resolveConflictNames(String ruleConflicts, Map<Integer, String> ruleNameById) {
    List<Integer> ids = parseConflictIds(ruleConflicts);
    if (ids.isEmpty()) {
      return null;
    }

    List<String> names = ids.stream()
        .map(id -> ruleNameById.getOrDefault(id, "#" + id))
        .toList();
    return String.join(", ", names);
  }

  private List<Integer> parseConflictIds(String raw) {
    if (!hasText(raw)) {
      return List.of();
    }

    String cleaned = raw.trim();
    if (cleaned.startsWith("[")) {
      cleaned = cleaned.substring(1);
    }
    if (cleaned.endsWith("]")) {
      cleaned = cleaned.substring(0, cleaned.length() - 1);
    }
    if (cleaned.isBlank()) {
      return List.of();
    }

    List<Integer> ids = new java.util.ArrayList<>();
    for (String token : cleaned.split(",")) {
      String trimmed = token.trim();
      if (trimmed.isBlank()) {
        continue;
      }
      try {
        ids.add(Integer.parseInt(trimmed));
      } catch (NumberFormatException ignored) {
        // keep robust against malformed legacy values
      }
    }
    return ids;
  }

  private int normalizePage(Integer page, int totalPages) {
    if (page == null || page < 0) {
      return 0;
    }
    return Math.min(page, totalPages - 1);
  }

  private int normalizePageSize(Integer pageSize) {
    if (pageSize == null || pageSize <= 0) {
      return 10;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private record LoadedTransactions(
      List<Transaction> transactions,
      Locale locale,
      Map<Integer, CategoryDisplay> categoryDisplayById,
      Map<Integer, String> ruleNameById,
      CategoryFilter categoryFilter) {}

  private record CategoryFilter(
      Integer categoryId,
      Integer parentCategoryId,
      Set<Integer> subcategoryIds,
      String label) {
    private static CategoryFilter empty() {
      return new CategoryFilter(null, null, Set.of(), null);
    }
  }

  private record CategoryDisplay(String label, boolean defaultCategory) {}
}
