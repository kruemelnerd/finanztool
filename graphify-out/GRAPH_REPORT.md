# Graph Report - .  (2026-06-12)

## Corpus Check
- 121 files · ~143,319 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1076 nodes · 2603 edges · 63 communities (31 shown, 32 thin omitted)
- Extraction: 57% EXTRACTED · 43% INFERRED · 0% AMBIGUOUS · INFERRED: 1111 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_CsvImportService Test|CsvImportService Test]]
- [[_COMMUNITY_AccountBalanceService|AccountBalanceService]]
- [[_COMMUNITY_Category|Category]]
- [[_COMMUNITY_CsvArtifactRepository|CsvArtifactRepository]]
- [[_COMMUNITY_RuleTextNormalizer|RuleTextNormalizer]]
- [[_COMMUNITY_RuleManagementService|RuleManagementService]]
- [[_COMMUNITY_CsvImportService|CsvImportService]]
- [[_COMMUNITY_TransactionsController|TransactionsController]]
- [[_COMMUNITY_CsvImportFlashService|CsvImportFlashService]]
- [[_COMMUNITY_TransactionViewService|TransactionViewService]]
- [[_COMMUNITY_PlaywrightE2E Test|PlaywrightE2E Test]]
- [[_COMMUNITY_CategoryTransferService Test|CategoryTransferService Test]]
- [[_COMMUNITY_WebSteps|WebSteps]]
- [[_COMMUNITY_CSV Artifact Import Model|CSV Artifact Import Model]]
- [[_COMMUNITY_CategoriesController|CategoriesController]]
- [[_COMMUNITY_Transaction|Transaction]]
- [[_COMMUNITY_UiSteps|UiSteps]]
- [[_COMMUNITY_RulesController|RulesController]]
- [[_COMMUNITY_V10__NormalizeCategoryBooleanColumns|V10__NormalizeCategoryBooleanColumns]]
- [[_COMMUNITY_Rule|Rule]]
- [[_COMMUNITY_RuleEngine|RuleEngine]]
- [[_COMMUNITY_403(b)|403(b)]]
- [[_COMMUNITY_RegistrationService Test|RegistrationService Test]]
- [[_COMMUNITY_Category Management|Category Management]]
- [[_COMMUNITY_CsvArtifactRepositoryIntegration Test|CsvArtifactRepositoryIntegration Test]]
- [[_COMMUNITY_CategoryAssignmentService Test|CategoryAssignmentService Test]]
- [[_COMMUNITY_CategoryAssignmentService|CategoryAssignmentService]]
- [[_COMMUNITY_Category Management|Category Management]]
- [[_COMMUNITY_DataDeletionServiceIntegration Test|DataDeletionServiceIntegration Test]]
- [[_COMMUNITY_TestHttpServletRequest|TestHttpServletRequest]]
- [[_COMMUNITY_BalanceDailyRepository|BalanceDailyRepository]]
- [[_COMMUNITY_BalanceService|BalanceService]]
- [[_COMMUNITY_Cashmax Dashboard|Cashmax Dashboard]]
- [[_COMMUNITY_TransactionRepository|TransactionRepository]]
- [[_COMMUNITY_BalanceDaily|BalanceDaily]]
- [[_COMMUNITY_User|User]]
- [[_COMMUNITY_Financial Dashboard Overview|Financial Dashboard Overview]]
- [[_COMMUNITY_AuthController|AuthController]]
- [[_COMMUNITY_CSV Upload Form|CSV Upload Form]]
- [[_COMMUNITY_Sankey-Report|Sankey-Report]]
- [[_COMMUNITY_LocaleConfig|LocaleConfig]]
- [[_COMMUNITY_SecurityConfig|SecurityConfig]]
- [[_COMMUNITY_RegistrationForm|RegistrationForm]]
- [[_COMMUNITY_RegistrationService|RegistrationService]]
- [[_COMMUNITY_RuleTextNormalizer Test|RuleTextNormalizer Test]]
- [[_COMMUNITY_Finanztool README|Finanztool README]]
- [[_COMMUNITY_CsvImportException|CsvImportException]]
- [[_COMMUNITY_RepositoryIntegration TestBase|RepositoryIntegration TestBase]]
- [[_COMMUNITY_DatabaseUserDetailsService|DatabaseUserDetailsService]]
- [[_COMMUNITY_FinanzappApplication|FinanzappApplication]]
- [[_COMMUNITY_LocaleConfigIntegration Test|LocaleConfigIntegration Test]]
- [[_COMMUNITY_CucumberSpringConfiguration|CucumberSpringConfiguration]]
- [[_COMMUNITY_Cucumber Test|Cucumber Test]]
- [[_COMMUNITY_Budget Sankey Diagram|Budget Sankey Diagram]]

## God Nodes (most connected - your core abstractions)
1. `Transaction` - 44 edges
2. `PagesControllerTest` - 40 edges
3. `CsvParser` - 34 edges
4. `PartialsController` - 34 edges
5. `WebSteps` - 31 edges
6. `TransactionViewService` - 30 edges
7. `PlaywrightE2ETest` - 29 edges
8. `RuleManagementService` - 27 edges
9. `Rule` - 24 edges
10. `CategoriesController` - 23 edges

## Surprising Connections (you probably didn't know these)
- `Categories Page Template` --implements--> `Default Category Set`  [INFERRED]
  src/main/resources/templates/categories.html → sdd/SDD_Kategorien_Regeln_Sankey.md
- `Repository Agent Guidance` --conceptually_related_to--> `Executable Specifications`  [INFERRED]
  AGENTS.md → sdd/SDD_Finanzapp_MVP.md
- `Release History Changelog` --conceptually_related_to--> `Release Automation Workflow`  [INFERRED]
  CHANGELOG.md → README.md
- `GitHub Upload Branch and PR Guide` --conceptually_related_to--> `Release Automation Workflow`  [INFERRED]
  docs/github-upload-anleitung.txt → README.md
- `Project TODO Status` --references--> `Category Rule Sankey Model`  [EXTRACTED]
  docs/project-todo-status.md → sdd/SDD_Kategorien_Regeln_Sankey.md

## Hyperedges (group relationships)
- **Overview Partial Composition** — overview_overview_page, balancechart_balance_chart_partial, recenttransactions_recent_transactions_partial, base_shared_layout [EXTRACTED 1.00]
- **Transactions Category Assignment Flow** — transactions_transactions_page, transactionstable_transactions_table_partial, categories_categories_page, sddcats_category_rule_sankey_model [INFERRED 0.82]
- **Release Pipeline Documentation** — readme_finanztool, changelog_release_history, jreleaser_release_config, readme_release_automation [INFERRED 0.84]
- **Financial Dashboard Layout** — 1_dashboard_overview, 1_sidebar_navigation, 1_kpi_summary_cards, 1_assets_line_chart, 1_cards_panel, 1_transactions_table, 1_transaction_gauge [EXTRACTED 1.00]
- **Cashmax Dashboard Overview Composition** — 2_cashmax_dashboard_ui, 2_sidebar_navigation, 2_balance_card, 2_multi_currency_cards, 2_financial_activity_monitor, 2_spending_tracker, 2_recent_transaction_list, 2_quick_send_panel [EXTRACTED 1.00]
- **Overview Dashboard Layout** — readme-overview_dashboard_overview_screen, readme-overview_sidebar_navigation, readme-overview_account_status_card, readme-overview_preview_panel, readme-overview_activity_section, readme-overview_recent_transactions_table [EXTRACTED 1.00]
- **Sankey Report Header Controls** — readme-sankey_sankey_report_page, readme-sankey_dark_mode_toggle, readme-sankey_logout_button, readme-sankey_year_filter [EXTRACTED 1.00]
- **Essen Category Group** — skribble_categories_top_level_category_essen, skribble_categories_subcategory_fastfood, skribble_categories_subcategory_restaurant, skribble_categories_subcategory_groceries [EXTRACTED 1.00]
- **Wohnen Category Group** — skribble_categories_top_level_category_wohnen, skribble_categories_subcategory_miete, skribble_categories_subcategory_nebenkosten, skribble_categories_subcategory_internet_telefon [EXTRACTED 1.00]
- **Category Management Actions** — skribble_categories_category_management_screen, skribble_categories_edit_action, skribble_categories_delete_action, skribble_categories_new_subcategory_button [EXTRACTED 1.00]
- **Essen Category Hierarchy** — skribble_categories_with_rules_essen_category, skribble_categories_with_rules_fastfood_subcategory, skribble_categories_with_rules_restaurant_subcategory, skribble_categories_with_rules_groceries_subcategory [EXTRACTED 1.00]
- **FastFood Rule Editing Flow** — skribble_categories_with_rules_fastfood_subcategory, skribble_categories_with_rules_rule_button, skribble_categories_with_rules_rules_modal, skribble_categories_with_rules_text_fragments [INFERRED 0.86]
- **Budget Sources Form Expenses** — sankey_example_wages, sankey_example_tax_refund, sankey_example_employer_403b, sankey_example_expenses [EXTRACTED 1.00]
- **Expenses Breakdown** — sankey_example_expenses, sankey_example_house, sankey_example_living, sankey_example_taxes, sankey_example_invest [EXTRACTED 1.00]
- **House Breakdown** — sankey_example_house, sankey_example_mortgage, sankey_example_property_tax, sankey_example_home_insurance [EXTRACTED 1.00]
- **Invest Breakdown** — sankey_example_invest, sankey_example_403b, sankey_example_ira, sankey_example_savings [EXTRACTED 1.00]

## Communities (63 total, 32 thin omitted)

### Community 0 - "CsvImportService Test"
Cohesion: 0.07
Nodes (4): CsvImportServiceTest, TransactionViewServiceTest, PagesControllerTest, PartialsControllerTest

### Community 1 - "AccountBalanceService"
Cohesion: 0.06
Nodes (6): AccountBalanceService, AccountBalanceServiceTest, CsvArtifact, CsvParserTest, CsvUploadService, CsvUploadServiceTest

### Community 2 - "Category"
Cohesion: 0.05
Nodes (7): CategoryBootstrapService, CategoryBootstrapServiceIntegrationTest, CategoryManagementService, CategoryTransferService, MutableParent, Category, CategoryRepository

### Community 3 - "CsvArtifactRepository"
Cohesion: 0.06
Nodes (4): OverviewController, OverviewControllerTest, PartialsController, CsvArtifactRepository

### Community 4 - "RuleTextNormalizer"
Cohesion: 0.08
Nodes (5): CsvParser, RuleTextNormalizer, missingCategory(), ready(), RuleTransferService

### Community 5 - "RuleManagementService"
Cohesion: 0.12
Nodes (3): RuleRepository, RuleManagementService, empty()

### Community 6 - "CsvImportService"
Cohesion: 0.1
Nodes (3): CsvImportService, SankeyController, SankeyReportService

### Community 7 - "TransactionsController"
Cohesion: 0.11
Nodes (6): TransactionFilterRequest, hasNextPage(), hasPreviousPage(), nextPage(), previousPage(), TransactionsController

### Community 8 - "CsvImportFlashService"
Cohesion: 0.1
Nodes (4): CsvImportFlashService, DataDeletionService, DataDeletionServiceTest, SettingsController

### Community 11 - "CategoryTransferService Test"
Cohesion: 0.11
Nodes (10): CategoryTransferServiceTest, FailingExportObjectMapper, FailingImportObjectMapper, TestJacksonException, JacksonException, ObjectMapper, FailingExportObjectMapper, FailingImportObjectMapper (+2 more)

### Community 13 - "CSV Artifact Import Model"
Cohesion: 0.15
Nodes (25): Repository Agent Guidance, Balance Chart Partial, Navigation and Theme Shell, Shared Base Layout Template, Categories Page Template, Login Page Template, Overview Page Template, Project TODO Status (+17 more)

### Community 18 - "V10__NormalizeCategoryBooleanColumns"
Cohesion: 0.25
Nodes (3): BaseJavaMigration, V10__NormalizeCategoryBooleanColumns, V6__BackfillTransactionBookingComponents

### Community 21 - "403(b)"
Cohesion: 0.13
Nodes (15): 403(b), Employer 403b, Expenses, Groceries, Home Insurance, House, Invest, IRA (+7 more)

### Community 23 - "Category Management"
Cohesion: 0.36
Nodes (14): Category Management Screen, Löschen Action, Bearbeiten Action, Hierarchical Category Structure, Neue Subkategorie Button, Skribble Categories UI Sketch, Subcategory FastFood, Subcategory Groceries (+6 more)

### Community 24 - "CsvArtifactRepositoryIntegration Test"
Cohesion: 0.18
Nodes (4): CsvArtifactRepositoryIntegrationTest, TransactionRepositoryIntegrationTest, UserRepositoryIntegrationTest, RepositoryIntegrationTestBase

### Community 27 - "Category Management"
Cohesion: 0.18
Nodes (13): Active or Inactive Toggle, Category Management Screen, Essen Category, Jetzt Ausfuhren Button, FastFood Subcategory, Groceries Subcategory, Skribble Categories With Rules Sketch, Neue Subkategorie Button (+5 more)

### Community 29 - "TestHttpServletRequest"
Cohesion: 0.23
Nodes (3): UploadExceptionHandler, TestHttpServletRequest, UploadExceptionHandlerTest

### Community 32 - "Cashmax Dashboard"
Cohesion: 0.29
Nodes (10): Balance Summary Card, Borderless World Upgrade Promo, Cashmax Brand, Cashmax Dashboard UI, Financial Activity Monitor, Multi-Currency Account Cards, Quick Send Panel, Recent Transaction List (+2 more)

### Community 37 - "Financial Dashboard Overview"
Cohesion: 0.33
Nodes (9): Assets Line Chart, Bank Cards Panel, Financial Dashboard Overview, Ethan Cole, Financial Management Interface, KPI Summary Cards, Sidebar Navigation, Transaction View Gauge (+1 more)

### Community 39 - "CSV Upload Form"
Cohesion: 0.32
Nodes (8): Account Status Card, Financial Activity Section, CSV Upload Form, Dashboard Overview Screen, Preview Panel, Recent Transactions Table, Sidebar Navigation, Dark Mode Toggle

### Community 40 - "Sankey-Report"
Cohesion: 0.33
Nodes (7): Dark Mode Toggle, No Data Empty State, Finanzapp Sidebar Navigation, Abmelden Button, Berichte Navigation Item, Sankey-Report Page, Year Filter

### Community 47 - "Finanztool README"
Cohesion: 0.5
Nodes (5): Release History Changelog, GitHub Upload Branch and PR Guide, JReleaser Release Config, Finanztool README, Release Automation Workflow

## Knowledge Gaps
- **42 isolated node(s):** `CucumberSpringConfiguration`, `CucumberTest`, `Finanztool README`, `GitHub Upload Branch and PR Guide`, `SDD Kategorien Regeln Sankey` (+37 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **32 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `UiSteps` connect `UiSteps` to `CsvImportService Test`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **Why does `Transaction` connect `Transaction` to `CsvImportService Test`, `AccountBalanceService`, `CsvImportService`, `RuleEngine`, `CategoryAssignmentService Test`, `CategoryAssignmentService`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **What connects `CucumberSpringConfiguration`, `CucumberTest`, `Finanztool README` to the rest of the system?**
  _42 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CsvImportService Test` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._
- **Should `AccountBalanceService` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._
- **Should `Category` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `CsvArtifactRepository` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._