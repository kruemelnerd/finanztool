Feature: Dashboard pages
  Scenario: Overview shows balance and transactions modules
    Given I am logged in
    When I request "/overview"
    Then I see text "Current balance"
    And I see text "Last bookings"
    And I see text "Upload CSV"

  Scenario: Transactions page shows filter form
    Given I am logged in
    When I request "/transactions"
    Then I see text "Min amount"
    And I see text "Max amount"
