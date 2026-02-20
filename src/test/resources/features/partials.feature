Feature: HTMX partials
  Scenario: Balance chart partial shows empty state
    Given I am logged in
    When I request "/partials/balance-chart"
    Then I see text "No balance data available."

  Scenario: Balance chart partial renders y-axis label and debit tooltip content
    Given I am logged in as "chart-user@example.com"
    And I have chart data with debit marker for "chart-user@example.com"
    When I request "/partials/balance-chart"
    Then I see text "Balance (EUR)"
    And I see text "Bookings on"
    And I see text "Coffee Shop"

  Scenario: Balance chart partial shows retry state on invalid range
    Given I am logged in as "chart-user@example.com"
    When I request "/partials/balance-chart?range=invalid"
    Then I see text "Chart could not be loaded right now. Please try again."
    And I see text "Retry"

  Scenario: Recent transactions partial shows empty state
    Given I am logged in
    When I request "/partials/recent-transactions"
    Then I see text "No transactions yet."

  Scenario: Transactions table partial shows empty state
    Given I am logged in
    When I request "/partials/transactions-table"
    Then I see text "No transactions to show."
