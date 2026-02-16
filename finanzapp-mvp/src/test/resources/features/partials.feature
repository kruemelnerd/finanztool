Feature: HTMX partials
  Scenario: Balance chart partial shows empty state
    Given I am logged in
    When I request "/partials/balance-chart"
    Then I see text "No balance data available."

  Scenario: Recent transactions partial shows empty state
    Given I am logged in
    When I request "/partials/recent-transactions"
    Then I see text "No transactions yet."

  Scenario: Transactions table partial shows empty state
    Given I am logged in
    When I request "/partials/transactions-table"
    Then I see text "No transactions to show."
