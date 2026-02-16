@ui
Feature: UI CSV import and filtering
  Scenario: Upload CSV and view transactions
    Given I am logged in via UI
    When I upload the sample CSV on overview
    And I open the transactions page
    Then I should see transaction name containing "Markt"
    And I should see transaction amount "-52.84 EUR"

  Scenario: Filter by min and max amount
    Given I am logged in via UI
    When I upload the sample CSV on overview
    And I open the transactions page
    And I filter transactions with min "50" max "100"
    Then I should see transaction amount "-52.84 EUR"
    And I should see transaction amount "-75.00 EUR"
    And I should not see transaction amount "-120.00 EUR"

  Scenario: Reset filters restores the default table
    Given I am logged in via UI
    When I upload the sample CSV on overview
    And I open the transactions page
    And I filter transactions with min "100" max ""
    Then I should see transaction amount "-120.00 EUR"
    And I should not see transaction amount "-52.84 EUR"
    And I reset the transaction filters
    Then I should see transaction amount "-120.00 EUR"
    And I should see transaction amount "-52.84 EUR"
