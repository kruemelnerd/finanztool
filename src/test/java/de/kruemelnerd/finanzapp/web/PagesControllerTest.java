package de.kruemelnerd.finanzapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.kruemelnerd.finanzapp.importcsv.CsvImportException;
import de.kruemelnerd.finanzapp.importcsv.CsvImportResult;
import de.kruemelnerd.finanzapp.importcsv.CsvUploadService;
import de.kruemelnerd.finanzapp.domain.Category;
import de.kruemelnerd.finanzapp.domain.CategoryAssignedBy;
import de.kruemelnerd.finanzapp.domain.Rule;
import de.kruemelnerd.finanzapp.domain.RuleMatchField;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.CategoryRepository;
import de.kruemelnerd.finanzapp.repository.RuleRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import de.kruemelnerd.finanzapp.settings.DataDeletionService;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class PagesControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private RuleRepository ruleRepository;

  @MockBean
  private CsvUploadService csvUploadService;

  @MockBean
  private DataDeletionService dataDeletionService;

  @Test
  void loginPageLoads() throws Exception {
    mockMvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Login")));
  }

  @Test
  void registerPageLoads() throws Exception {
    mockMvc.perform(get("/register"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Create account")));
  }

  @Test
  void rootRedirectsWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  @Test
  void rootRedirectsToOverviewForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/").with(user("user")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/overview"));
  }

  @Test
  void overviewRedirectsWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/overview"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  @Test
  void overviewLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/overview").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Current balance")))
        .andExpect(content().string(containsString("Last bookings")));
  }

  @Test
  void transactionsPageLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/transactions").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Min amount")));
  }

  @Test
  void settingsPageLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/settings").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Delete account")));
  }

  @Test
  void rulesPageRedirectsToCategoriesForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/rules").with(user("user")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/categories"));
  }

  @Test
  void categoriesPageLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/categories").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("New category")));
  }

  @Test
  void categoriesPageShowsUsageLinksForParentAndSubcategory() throws Exception {
    createUser("categories-usage@example.com");
    User owner = userRepository.findByEmail("categories-usage@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category subcategory = new Category();
    subcategory.setUser(owner);
    subcategory.setParent(parent);
    subcategory.setName("Sport");
    subcategory.setSortOrder(0);
    subcategory = categoryRepository.save(subcategory);

    Transaction tx = new Transaction();
    tx.setUser(owner);
    tx.setBookingDateTime(LocalDateTime.of(2026, 2, 5, 9, 0));
    tx.setPartnerName("Intersport");
    tx.setPurposeText("Shoe purchase");
    tx.setAmountCents(-12999L);
    tx.setCategory(subcategory);
    transactionRepository.save(tx);

    mockMvc.perform(get("/categories").with(user("categories-usage@example.com")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("/transactions?parentCategoryId=" + parent.getId())))
        .andExpect(content().string(containsString("/transactions?subcategoryId=" + subcategory.getId())));
  }

  @Test
  void categoriesPageShowsRuleButtonAndIndicatorState() throws Exception {
    createUser("categories-rules-view@example.com");
    User owner = userRepository.findByEmail("categories-rules-view@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category activeSub = new Category();
    activeSub.setUser(owner);
    activeSub.setParent(parent);
    activeSub.setName("ActiveRuleSub");
    activeSub.setSortOrder(0);
    activeSub = categoryRepository.save(activeSub);

    Category inactiveSub = new Category();
    inactiveSub.setUser(owner);
    inactiveSub.setParent(parent);
    inactiveSub.setName("InactiveRuleSub");
    inactiveSub.setSortOrder(1);
    inactiveSub = categoryRepository.save(inactiveSub);

    Rule activeRule = new Rule();
    activeRule.setUser(owner);
    activeRule.setName("Active Rule");
    activeRule.setMatchText("active");
    activeRule.setMatchField(RuleMatchField.BOTH);
    activeRule.setCategory(activeSub);
    activeRule.setActive(true);
    activeRule.setSortOrder(0);
    ruleRepository.save(activeRule);

    Rule inactiveRule = new Rule();
    inactiveRule.setUser(owner);
    inactiveRule.setName("Inactive Rule");
    inactiveRule.setMatchText("inactive");
    inactiveRule.setMatchField(RuleMatchField.BOTH);
    inactiveRule.setCategory(inactiveSub);
    inactiveRule.setActive(false);
    inactiveRule.setSortOrder(1);
    ruleRepository.save(inactiveRule);

    mockMvc.perform(get("/categories").with(user("categories-rules-view@example.com")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-rule-open=\"" + activeSub.getId() + "\"")))
        .andExpect(content().string(containsString("data-rule-open=\"" + inactiveSub.getId() + "\"")))
        .andExpect(content().string(containsString("category-rule-button-active")))
        .andExpect(content().string(containsString("category-rule-button-inactive")));
  }

  @Test
  void categoriesExportReturnsJsonAttachment() throws Exception {
    createUser("categories-export@example.com");
    User owner = userRepository.findByEmail("categories-export@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Sport");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    Rule categoryRule = new Rule();
    categoryRule.setUser(owner);
    categoryRule.setName("Sport Rule");
    categoryRule.setMatchText("intersport");
    categoryRule.setMatchField(RuleMatchField.BOTH);
    categoryRule.setCategory(sub);
    categoryRule.setActive(false);
    categoryRule.setSortOrder(0);
    ruleRepository.save(categoryRule);

    mockMvc.perform(get("/categories/export").with(user("categories-export@example.com")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("application/json")))
        .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"categories-export.json\"")))
        .andExpect(content().string(containsString("\"format\" : \"finanztool-categories-v1\"")))
        .andExpect(content().string(containsString("\"name\" : \"Shopping\"")))
        .andExpect(content().string(containsString("\"Sport\"")))
        .andExpect(content().string(containsString("\"ruleGroups\"")))
        .andExpect(content().string(containsString("\"parentCategory\" : \"Shopping\"")))
        .andExpect(content().string(containsString("\"category\" : \"Sport\"")))
        .andExpect(content().string(containsString("\"active\" : false")))
        .andExpect(content().string(containsString("\"intersport\"")));
  }

  @Test
  void categoriesImportAddsMissingParentsAndSubcategories() throws Exception {
    createUser("categories-import@example.com");
    User owner = userRepository.findByEmail("categories-import@example.com").orElseThrow();

    Category shopping = new Category();
    shopping.setUser(owner);
    shopping.setName("Shopping");
    shopping.setSortOrder(0);
    shopping.setDefault(false);
    shopping.setSystem(false);
    shopping = categoryRepository.save(shopping);

    Category existingSub = new Category();
    existingSub.setUser(owner);
    existingSub.setParent(shopping);
    existingSub.setName("Sport");
    existingSub.setSortOrder(0);
    existingSub.setDefault(false);
    existingSub.setSystem(false);
    categoryRepository.save(existingSub);

    String importJson = """
        {
          "format": "finanztool-categories-v1",
          "parents": [
            {
              "name": "Shopping",
              "subcategories": ["Sport", "Shoes"]
            },
            {
              "name": "Travel",
              "subcategories": ["Flights", "Hotels"]
            }
          ]
        }
        """;

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "categories.json",
        "application/json",
        importJson.getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart("/categories/import")
            .file(file)
            .with(user("categories-import@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/categories"))
        .andExpect(flash().attribute("categoriesStatus", "success"));

    java.util.List<Category> parents =
        categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(owner);
    org.assertj.core.api.Assertions.assertThat(parents)
        .extracting(Category::getName)
        .contains("Shopping", "Travel");

    Category travel = parents.stream()
        .filter(parent -> "Travel".equals(parent.getName()))
        .findFirst()
        .orElseThrow();
    java.util.List<Category> travelSubcategories =
        categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, travel);
    org.assertj.core.api.Assertions.assertThat(travelSubcategories)
        .extracting(Category::getName)
        .containsExactly("Flights", "Hotels");

    Category persistedShopping = parents.stream()
        .filter(parent -> "Shopping".equals(parent.getName()))
        .findFirst()
        .orElseThrow();
    java.util.List<Category> shoppingSubcategories =
        categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, persistedShopping);
    org.assertj.core.api.Assertions.assertThat(shoppingSubcategories)
        .extracting(Category::getName)
        .containsExactly("Sport", "Shoes");
  }

  @Test
  void upsertSubcategoryRuleFromCategoriesPageCreatesAndUpdatesRules() throws Exception {
    createUser("categories-rules-upsert@example.com");
    User owner = userRepository.findByEmail("categories-rules-upsert@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Food");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("FastFood");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    mockMvc.perform(post("/categories/subcategories/" + sub.getId() + "/rule")
            .with(user("categories-rules-upsert@example.com"))
            .with(csrf())
            .param("fragmentsText", "burger king\nmcdonalds")
            .param("active", "false"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/categories"))
        .andExpect(flash().attribute("categoriesStatus", "success"));

    java.util.List<Rule> createdRules =
        ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, sub.getId());
    org.assertj.core.api.Assertions.assertThat(createdRules).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(createdRules)
        .extracting(Rule::getMatchText)
        .containsExactly("burger king", "mcdonalds");
    org.assertj.core.api.Assertions.assertThat(createdRules).allMatch(rule -> !rule.isActive());

    mockMvc.perform(post("/categories/subcategories/" + sub.getId() + "/rule")
            .with(user("categories-rules-upsert@example.com"))
            .with(csrf())
            .param("fragmentsText", "pizza hut\nsubway")
            .param("active", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/categories"))
        .andExpect(flash().attribute("categoriesStatus", "success"));

    java.util.List<Rule> updatedRules =
        ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, sub.getId());
    org.assertj.core.api.Assertions.assertThat(updatedRules).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(updatedRules)
        .extracting(Rule::getMatchText)
        .containsExactly("pizza hut", "subway");
    org.assertj.core.api.Assertions.assertThat(updatedRules).allMatch(Rule::isActive);
  }

  @Test
  void deleteSubcategoryRuleFromCategoriesPageSoftDeletesRules() throws Exception {
    createUser("categories-rules-delete@example.com");
    User owner = userRepository.findByEmail("categories-rules-delete@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Food");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Takeaway");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    Rule first = new Rule();
    first.setUser(owner);
    first.setName("Rule One");
    first.setMatchText("first");
    first.setMatchField(RuleMatchField.BOTH);
    first.setCategory(sub);
    first.setSortOrder(0);
    first = ruleRepository.save(first);

    Rule second = new Rule();
    second.setUser(owner);
    second.setName("Rule Two");
    second.setMatchText("second");
    second.setMatchField(RuleMatchField.BOTH);
    second.setCategory(sub);
    second.setSortOrder(1);
    second = ruleRepository.save(second);

    mockMvc.perform(post("/categories/subcategories/" + sub.getId() + "/rule/delete")
            .with(user("categories-rules-delete@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/categories"))
        .andExpect(flash().attribute("categoriesStatus", "success"));

    org.assertj.core.api.Assertions.assertThat(
        ruleRepository.findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, sub.getId()))
        .isEmpty();
    org.assertj.core.api.Assertions.assertThat(ruleRepository.findById(first.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    org.assertj.core.api.Assertions.assertThat(ruleRepository.findById(second.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
  }

  @Test
  void transactionsPageCanFilterBySubcategoryAndParentCategory() throws Exception {
    createUser("transactions-category-filter@example.com");
    User owner = userRepository.findByEmail("transactions-category-filter@example.com").orElseThrow();

    Category shopping = new Category();
    shopping.setUser(owner);
    shopping.setName("Shopping");
    shopping.setSortOrder(0);
    shopping = categoryRepository.save(shopping);

    Category sport = new Category();
    sport.setUser(owner);
    sport.setParent(shopping);
    sport.setName("Sport");
    sport.setSortOrder(0);
    sport = categoryRepository.save(sport);

    Category books = new Category();
    books.setUser(owner);
    books.setParent(shopping);
    books.setName("Books");
    books.setSortOrder(1);
    books = categoryRepository.save(books);

    Category mobility = new Category();
    mobility.setUser(owner);
    mobility.setName("Mobility");
    mobility.setSortOrder(1);
    mobility = categoryRepository.save(mobility);

    Category fuel = new Category();
    fuel.setUser(owner);
    fuel.setParent(mobility);
    fuel.setName("Fuel");
    fuel.setSortOrder(0);
    fuel = categoryRepository.save(fuel);

    Transaction sportTx = new Transaction();
    sportTx.setUser(owner);
    sportTx.setBookingDateTime(LocalDateTime.of(2026, 2, 6, 9, 0));
    sportTx.setPartnerName("Sport Shop");
    sportTx.setPurposeText("Sport");
    sportTx.setAmountCents(-5000L);
    sportTx.setCategory(sport);
    transactionRepository.save(sportTx);

    Transaction booksTx = new Transaction();
    booksTx.setUser(owner);
    booksTx.setBookingDateTime(LocalDateTime.of(2026, 2, 7, 9, 0));
    booksTx.setPartnerName("Book Store");
    booksTx.setPurposeText("Books");
    booksTx.setAmountCents(-3000L);
    booksTx.setCategory(books);
    transactionRepository.save(booksTx);

    Transaction fuelTx = new Transaction();
    fuelTx.setUser(owner);
    fuelTx.setBookingDateTime(LocalDateTime.of(2026, 2, 8, 9, 0));
    fuelTx.setPartnerName("Fuel Station");
    fuelTx.setPurposeText("Fuel");
    fuelTx.setAmountCents(-4500L);
    fuelTx.setCategory(fuel);
    transactionRepository.save(fuelTx);

    mockMvc.perform(get("/transactions")
            .with(user("transactions-category-filter@example.com"))
            .param("subcategoryId", sport.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Sport Shop")))
        .andExpect(content().string(not(containsString("Book Store"))))
        .andExpect(content().string(not(containsString("Fuel Station"))));

    mockMvc.perform(get("/transactions")
            .with(user("transactions-category-filter@example.com"))
            .param("parentCategoryId", shopping.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Sport Shop")))
        .andExpect(content().string(containsString("Book Store")))
        .andExpect(content().string(not(containsString("Fuel Station"))));
  }

  @Test
  void sankeyPageLoadsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/reports/sankey").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Year")));
  }

  @Test
  void sankeyApiReturnsIncomeAndExpenseFlowsForSelectedYear() throws Exception {
    createUser("sankey-user@example.com");
    User owner = userRepository.findByEmail("sankey-user@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Sankey Parent");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Sankey Sub");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    Transaction income = new Transaction();
    income.setUser(owner);
    income.setBookingDateTime(LocalDateTime.of(2025, 2, 5, 12, 0));
    income.setPartnerName("Employer");
    income.setPurposeText("Salary");
    income.setAmountCents(150000L);
    income.setCategory(sub);
    transactionRepository.save(income);

    Transaction expense = new Transaction();
    expense.setUser(owner);
    expense.setBookingDateTime(LocalDateTime.of(2025, 2, 7, 18, 0));
    expense.setPartnerName("Supermarkt");
    expense.setPurposeText("Groceries");
    expense.setAmountCents(-4200L);
    expense.setCategory(sub);
    transactionRepository.save(expense);

    mockMvc.perform(get("/api/reports/sankey")
            .with(user("sankey-user@example.com"))
            .param("year", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.year", is(2025)))
        .andExpect(jsonPath("$.links[?(@.target=='center')].valueCents", hasItem(150000)))
        .andExpect(jsonPath("$.links[?(@.source=='center')].valueCents", hasItem(4200)));
  }

  @Test
  void createRulePersistsRuleGroupWithMultipleFragments() throws Exception {
    createUser("rules-create@example.com");
    User owner = userRepository.findByEmail("rules-create@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Sport");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);
    Integer subId = sub.getId();

    mockMvc.perform(post("/rules")
            .with(user("rules-create@example.com"))
            .with(csrf())
            .param("fragmentsText", "intersport\nsportcheck")
            .param("categoryId", subId.toString()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/rules"))
        .andExpect(flash().attribute("rulesStatus", "success"));

    java.util.List<Rule> rules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner);
    org.assertj.core.api.Assertions.assertThat(rules).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(rules)
        .extracting(Rule::getMatchText)
        .containsExactly("intersport", "sportcheck");
    org.assertj.core.api.Assertions.assertThat(rules)
        .allMatch(rule -> rule.getMatchField() == RuleMatchField.BOTH);
    org.assertj.core.api.Assertions.assertThat(rules)
        .allMatch(rule -> subId.equals(rule.getCategory().getId()));
  }

  @Test
  void moveRuleUpSwapsCategoryOrder() throws Exception {
    createUser("rules-order@example.com");
    User owner = userRepository.findByEmail("rules-order@example.com").orElseThrow();

    Category firstParent = new Category();
    firstParent.setUser(owner);
    firstParent.setName("Shopping");
    firstParent.setSortOrder(0);
    firstParent = categoryRepository.save(firstParent);

    Category firstSub = new Category();
    firstSub.setUser(owner);
    firstSub.setParent(firstParent);
    firstSub.setName("Sport");
    firstSub.setSortOrder(0);
    firstSub = categoryRepository.save(firstSub);

    Category secondParent = new Category();
    secondParent.setUser(owner);
    secondParent.setName("Food");
    secondParent.setSortOrder(1);
    secondParent = categoryRepository.save(secondParent);

    Category secondSub = new Category();
    secondSub.setUser(owner);
    secondSub.setParent(secondParent);
    secondSub.setName("FastFood");
    secondSub.setSortOrder(0);
    secondSub = categoryRepository.save(secondSub);

    Rule first = new Rule();
    first.setUser(owner);
    first.setName("Rule One");
    first.setMatchText("one");
    first.setMatchField(RuleMatchField.BOTH);
    first.setCategory(firstSub);
    first.setSortOrder(0);
    ruleRepository.save(first);

    Rule second = new Rule();
    second.setUser(owner);
    second.setName("Rule Two");
    second.setMatchText("two");
    second.setMatchField(RuleMatchField.BOTH);
    second.setCategory(secondSub);
    second.setSortOrder(1);
    ruleRepository.save(second);

    mockMvc.perform(post("/rules/" + secondSub.getId() + "/move-up")
            .with(user("rules-order@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/rules"))
        .andExpect(flash().attribute("rulesStatus", "success"));

    java.util.List<Rule> rules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner);
    org.assertj.core.api.Assertions.assertThat(rules).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(rules.get(0).getCategory().getId()).isEqualTo(secondSub.getId());
    org.assertj.core.api.Assertions.assertThat(rules.get(1).getCategory().getId()).isEqualTo(firstSub.getId());
  }

  @Test
  void runSingleCategoryRuleUpdatesMatchingTransactionAndRuleMetadata() throws Exception {
    createUser("rules-run@example.com");
    User owner = userRepository.findByEmail("rules-run@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Sport");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    Rule rule = new Rule();
    rule.setUser(owner);
    rule.setName("InterSport");
    rule.setMatchText("intersport");
    rule.setMatchField(RuleMatchField.BOTH);
    rule.setCategory(sub);
    rule.setSortOrder(0);
    rule = ruleRepository.save(rule);

    Transaction transaction = new Transaction();
    transaction.setUser(owner);
    transaction.setBookingDateTime(LocalDateTime.of(2026, 2, 8, 10, 0));
    transaction.setPartnerName("InterSport Berlin");
    transaction.setPurposeText("Buchungstext: Schuhe");
    transaction.setAmountCents(-4900L);
    transaction = transactionRepository.save(transaction);

    mockMvc.perform(post("/rules/" + sub.getId() + "/run")
            .with(user("rules-run@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/rules"))
        .andExpect(flash().attribute("rulesStatus", "success"));

    Rule updatedRule = ruleRepository.findById(rule.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updatedRule.getLastRunAt()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(updatedRule.getLastMatchCount()).isEqualTo(1);

    Transaction updatedTx = transactionRepository.findById(transaction.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updatedTx.getCategory()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(updatedTx.getCategory().getId()).isEqualTo(sub.getId());
    org.assertj.core.api.Assertions.assertThat(updatedTx.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.RULE);
  }

  @Test
  void rulesExportReturnsJsonAttachment() throws Exception {
    createUser("rules-export@example.com");
    User owner = userRepository.findByEmail("rules-export@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Sport");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    Rule first = new Rule();
    first.setUser(owner);
    first.setName("Rule One");
    first.setMatchText("intersport");
    first.setMatchField(RuleMatchField.BOTH);
    first.setCategory(sub);
    first.setSortOrder(0);
    ruleRepository.save(first);

    Rule second = new Rule();
    second.setUser(owner);
    second.setName("Rule Two");
    second.setMatchText("sportcheck");
    second.setMatchField(RuleMatchField.BOTH);
    second.setCategory(sub);
    second.setSortOrder(1);
    ruleRepository.save(second);

    mockMvc.perform(get("/rules/export").with(user("rules-export@example.com")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("application/json")))
        .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"rules-export.json\"")))
        .andExpect(content().string(containsString("\"format\" : \"finanztool-rules-v1\"")))
        .andExpect(content().string(containsString("\"parentCategory\" : \"Shopping\"")))
        .andExpect(content().string(containsString("\"category\" : \"Sport\"")))
        .andExpect(content().string(containsString("\"intersport\"")));
  }

  @Test
  void rulesImportMergesRulesFromJsonFile() throws Exception {
    createUser("rules-import@example.com");
    User owner = userRepository.findByEmail("rules-import@example.com").orElseThrow();

    Category oldParent = new Category();
    oldParent.setUser(owner);
    oldParent.setName("Legacy");
    oldParent.setSortOrder(0);
    oldParent = categoryRepository.save(oldParent);

    Category oldSub = new Category();
    oldSub.setUser(owner);
    oldSub.setParent(oldParent);
    oldSub.setName("Old");
    oldSub.setSortOrder(0);
    oldSub = categoryRepository.save(oldSub);

    Rule oldRule = new Rule();
    oldRule.setUser(owner);
    oldRule.setName("Legacy Rule");
    oldRule.setMatchText("legacy");
    oldRule.setMatchField(RuleMatchField.BOTH);
    oldRule.setCategory(oldSub);
    oldRule.setSortOrder(0);
    oldRule = ruleRepository.save(oldRule);

    Category shoppingParent = new Category();
    shoppingParent.setUser(owner);
    shoppingParent.setName("Shopping");
    shoppingParent.setSortOrder(1);
    shoppingParent = categoryRepository.save(shoppingParent);

    Category sportSub = new Category();
    sportSub.setUser(owner);
    sportSub.setParent(shoppingParent);
    sportSub.setName("Sport");
    sportSub.setSortOrder(0);
    sportSub = categoryRepository.save(sportSub);
    Integer sportSubId = sportSub.getId();

    String importJson = """
        {
          "format": "finanztool-rules-v1",
          "groups": [
            {
              "parentCategory": "Shopping",
              "category": "Sport",
              "matchField": "BOTH",
              "active": true,
              "fragments": ["intersport", "sportcheck"]
            }
          ]
        }
        """;

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "rules.json",
        "application/json",
        importJson.getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart("/rules/import")
            .file(file)
            .with(user("rules-import@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/rules"))
        .andExpect(flash().attribute("rulesStatus", "success"));

    Rule persistedOldRule = ruleRepository.findById(oldRule.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(persistedOldRule.getDeletedAt()).isNull();

    java.util.List<Rule> activeRules = ruleRepository.findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner);
    org.assertj.core.api.Assertions.assertThat(activeRules).hasSize(3);
    org.assertj.core.api.Assertions.assertThat(activeRules)
        .extracting(Rule::getMatchText)
        .containsExactly("legacy", "intersport", "sportcheck");
    org.assertj.core.api.Assertions.assertThat(activeRules)
        .filteredOn(rule -> !"legacy".equals(rule.getMatchText()))
        .allMatch(rule -> sportSubId.equals(rule.getCategory().getId()));
  }

  @Test
  void updateProfilePersistsDisplayName() throws Exception {
    createUser("user@example.com");

    mockMvc.perform(post("/settings/profile")
            .with(user("user@example.com"))
            .with(csrf())
            .param("displayName", "Alex"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsStatus", "success"));

    Optional<User> saved = userRepository.findByEmail("user@example.com");
    org.assertj.core.api.Assertions.assertThat(saved).isPresent();
    org.assertj.core.api.Assertions.assertThat(saved.get().getDisplayName()).isEqualTo("Alex");
  }

  @Test
  void updateLanguagePersistsLanguage() throws Exception {
    createUser("user@example.com");

    mockMvc.perform(post("/settings/language")
            .with(user("user@example.com"))
            .with(csrf())
            .param("language", "DE"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsStatus", "success"));

    Optional<User> saved = userRepository.findByEmail("user@example.com");
    org.assertj.core.api.Assertions.assertThat(saved).isPresent();
    org.assertj.core.api.Assertions.assertThat(saved.get().getLanguage()).isEqualTo("DE");
  }

  @Test
  void loginRestoresLanguageFromSettings() throws Exception {
    createUserWithPasswordAndLanguage("user@example.com", "password123", "EN");

    MvcResult result = mockMvc.perform(post("/login")
            .param("username", "user@example.com")
            .param("password", "password123")
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/overview"))
        .andReturn();

    Locale locale = (Locale) result.getRequest().getSession().getAttribute(
        SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME);
    org.assertj.core.api.Assertions.assertThat(locale).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void deleteTransactionRedirects() throws Exception {
    createUser("user@example.com");
    User owner = userRepository.findByEmail("user@example.com").orElseThrow();

    Transaction tx = new Transaction();
    tx.setUser(owner);
    tx.setBookingDateTime(LocalDateTime.of(2026, 2, 6, 12, 0));
    tx.setPartnerName("Sample");
    tx.setPurposeText("Sample");
    tx.setAmountCents(-1000L);
    tx = transactionRepository.save(tx);

    mockMvc.perform(post("/transactions/" + tx.getId() + "/delete").with(user("user@example.com")).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/transactions"))
        .andExpect(flash().attribute("transactionStatus", "success"));

    Transaction deleted = transactionRepository.findById(tx.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(deleted.getDeletedAt()).isNotNull();
  }

  @Test
  void setTransactionCategoryStoresManualAssignment() throws Exception {
    createUser("user@example.com");
    User owner = userRepository.findByEmail("user@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    Category sub = new Category();
    sub.setUser(owner);
    sub.setParent(parent);
    sub.setName("Sport");
    sub.setSortOrder(0);
    sub = categoryRepository.save(sub);

    Transaction tx = new Transaction();
    tx.setUser(owner);
    tx.setBookingDateTime(LocalDateTime.of(2026, 2, 6, 12, 0));
    tx.setPartnerName("Sample");
    tx.setPurposeText("Sample");
    tx.setAmountCents(-1000L);
    tx = transactionRepository.save(tx);

    mockMvc.perform(post("/transactions/" + tx.getId() + "/set-category")
            .with(user("user@example.com"))
            .with(csrf())
            .param("categoryId", sub.getId().toString()))
        .andExpect(status().is3xxRedirection());

    Transaction updated = transactionRepository.findById(tx.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getCategory()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(updated.getCategory().getId()).isEqualTo(sub.getId());
    org.assertj.core.api.Assertions.assertThat(updated.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.MANUAL);
    org.assertj.core.api.Assertions.assertThat(updated.isCategoryLocked()).isTrue();
  }

  @Test
  void createSubcategoryStoresNewEntry() throws Exception {
    createUser("categories-create@example.com");
    User owner = userRepository.findByEmail("categories-create@example.com").orElseThrow();

    Category parent = new Category();
    parent.setUser(owner);
    parent.setName("Shopping");
    parent.setSortOrder(0);
    parent = categoryRepository.save(parent);

    mockMvc.perform(post("/categories/subcategories")
            .with(user("categories-create@example.com"))
            .with(csrf())
            .param("parentId", parent.getId().toString())
            .param("name", "Sport"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/categories"))
        .andExpect(flash().attribute("categoriesStatus", "success"));

    java.util.List<Category> children = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, parent);
    org.assertj.core.api.Assertions.assertThat(children)
        .extracting(Category::getName)
        .contains("Sport");
  }

  @Test
  void updateSubcategoryCanMoveToOtherParent() throws Exception {
    createUser("categories-update@example.com");
    User owner = userRepository.findByEmail("categories-update@example.com").orElseThrow();

    Category firstParent = new Category();
    firstParent.setUser(owner);
    firstParent.setName("Shopping");
    firstParent.setSortOrder(0);
    firstParent = categoryRepository.save(firstParent);

    Category secondParent = new Category();
    secondParent.setUser(owner);
    secondParent.setName("Freizeit");
    secondParent.setSortOrder(1);
    secondParent = categoryRepository.save(secondParent);

    Category subcategory = new Category();
    subcategory.setUser(owner);
    subcategory.setParent(firstParent);
    subcategory.setName("Alt");
    subcategory.setSortOrder(0);
    subcategory = categoryRepository.save(subcategory);

    mockMvc.perform(post("/categories/subcategories/" + subcategory.getId())
            .with(user("categories-update@example.com"))
            .with(csrf())
            .param("parentId", secondParent.getId().toString())
            .param("name", "Neu"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/categories"))
        .andExpect(flash().attribute("categoriesStatus", "success"));

    Category persisted = categoryRepository.findById(subcategory.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(persisted.getName()).isEqualTo("Neu");
    org.assertj.core.api.Assertions.assertThat(persisted.getParent()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(persisted.getParent().getId()).isEqualTo(secondParent.getId());
  }

  @Test
  void reorderCategoriesPersistsParentAndSubcategoryOrder() throws Exception {
    createUser("categories-reorder@example.com");
    mockMvc.perform(get("/categories").with(user("categories-reorder@example.com")))
        .andExpect(status().isOk());

    User owner = userRepository.findByEmail("categories-reorder@example.com").orElseThrow();

    Category firstParent = new Category();
    firstParent.setUser(owner);
    firstParent.setName("TestParentA");
    firstParent.setSortOrder(0);
    firstParent = categoryRepository.save(firstParent);

    Category secondParent = new Category();
    secondParent.setUser(owner);
    secondParent.setName("TestParentB");
    secondParent.setSortOrder(1);
    secondParent = categoryRepository.save(secondParent);

    Category firstSub = new Category();
    firstSub.setUser(owner);
    firstSub.setParent(firstParent);
    firstSub.setName("TestSubA");
    firstSub.setSortOrder(0);
    firstSub = categoryRepository.save(firstSub);

    Category secondSub = new Category();
    secondSub.setUser(owner);
    secondSub.setParent(secondParent);
    secondSub.setName("TestSubB");
    secondSub.setSortOrder(0);
    secondSub = categoryRepository.save(secondSub);

    java.util.List<Category> orderedParents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(owner);
    java.util.List<Integer> reorderedParentIds = new ArrayList<>(orderedParents.stream().map(Category::getId).toList());
    reorderedParentIds.remove(Integer.valueOf(secondParent.getId()));
    reorderedParentIds.add(reorderedParentIds.indexOf(firstParent.getId()), secondParent.getId());

    Map<Integer, java.util.List<Integer>> childIdsByParent = new LinkedHashMap<>();
    for (Category parent : orderedParents) {
      java.util.List<Integer> childIds = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, parent)
          .stream()
          .map(Category::getId)
          .toList();
      childIdsByParent.put(parent.getId(), new ArrayList<>(childIds));
    }

    childIdsByParent.get(firstParent.getId()).remove(Integer.valueOf(firstSub.getId()));
    childIdsByParent.get(secondParent.getId()).add(firstSub.getId());

    java.util.List<Map<String, Object>> parentCommands = new ArrayList<>();
    for (Integer parentId : reorderedParentIds) {
      parentCommands.add(Map.of(
          "parentId", parentId,
          "subcategoryIds", childIdsByParent.getOrDefault(parentId, java.util.List.of())));
    }
    String payload = new com.fasterxml.jackson.databind.ObjectMapper()
        .writeValueAsString(Map.of("parents", parentCommands));

    mockMvc.perform(post("/categories/reorder")
            .with(user("categories-reorder@example.com"))
            .with(csrf().asHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    java.util.List<Category> parents = categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(owner);
    java.util.List<Integer> parentIds = parents.stream().map(Category::getId).toList();
    org.assertj.core.api.Assertions.assertThat(parentIds).contains(secondParent.getId(), firstParent.getId());
    org.assertj.core.api.Assertions.assertThat(parentIds.indexOf(secondParent.getId()))
        .isLessThan(parentIds.indexOf(firstParent.getId()));

    java.util.List<Category> secondParentChildren = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, secondParent);
    org.assertj.core.api.Assertions.assertThat(secondParentChildren)
        .extracting(Category::getId)
        .containsExactly(secondSub.getId(), firstSub.getId());

    java.util.List<Category> firstParentChildren = categoryRepository.findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner, firstParent);
    org.assertj.core.api.Assertions.assertThat(firstParentChildren).isEmpty();
  }

  @Test
  void deleteAllDataRedirectsToOverview() throws Exception {
    createUser("user");
    mockMvc.perform(post("/settings/delete-all-data").with(user("user"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/overview"));
  }

  @Test
  void deleteAccountRedirectsToLogin() throws Exception {
    createUser("user");
    mockMvc.perform(post("/settings/delete-account").with(user("user"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  private void createUser(String email) {
    userRepository.findByEmail(email).ifPresent(userRepository::delete);
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hashed");
    userRepository.save(user);
  }

  private void createUserWithPasswordAndLanguage(String email, String rawPassword, String language) {
    userRepository.findByEmail(email).ifPresent(userRepository::delete);
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setLanguage(language);
    userRepository.save(user);
  }

  @Test
  void csvUploadRedirectsWithSuccessMessage() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "import.csv", "text/csv", "data".getBytes());

    when(csvUploadService.importForEmail(eq("user@example.com"), any(MultipartFile.class)))
        .thenReturn(new CsvImportResult(2, 0, java.util.List.of()));

    mockMvc.perform(multipart("/settings/import-csv")
            .file(file)
            .with(user("user@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("csvImportStatus", "success"))
        .andExpect(flash().attribute("csvImportMessage", anyOf(
            is("2 transactions imported."),
            is("2 Buchungen importiert."))));
  }

  @Test
  void csvUploadRedirectsWithErrorMessage() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "import.csv", "text/csv", "".getBytes());

    when(csvUploadService.importForEmail(eq("user@example.com"), any(MultipartFile.class)))
        .thenThrow(new CsvImportException("CSV file is empty"));

    mockMvc.perform(multipart("/settings/import-csv")
            .file(file)
            .with(user("user@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("csvImportStatus", "error"))
        .andExpect(flash().attribute("csvImportMessage", "CSV file is empty"));
  }

  @Test
  void overviewCsvUploadRedirectsWithSuccessMessage() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "import.csv", "text/csv", "data".getBytes());

    when(csvUploadService.importForEmail(eq("user@example.com"), any(MultipartFile.class)))
        .thenReturn(new CsvImportResult(1, 0, java.util.List.of()));

    mockMvc.perform(multipart("/overview/import-csv")
            .file(file)
            .with(user("user@example.com"))
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/overview"))
        .andExpect(flash().attribute("csvImportStatus", "success"))
        .andExpect(flash().attribute("csvImportMessage", anyOf(
            is("1 transactions imported."),
            is("1 Buchungen importiert."))));
  }
}
