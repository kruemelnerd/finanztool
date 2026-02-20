@ui
Feature: UI delete flows with confirmation
  Scenario: Cancel transaction deletion keeps transaction visible
    Given I am logged in via UI
    And I upload the sample CSV on overview
    And I open the transactions page
    And I cancel the next confirmation dialog
    When I delete the transaction with amount "-52.84 EUR"
    Then I should see transaction amount "-52.84 EUR"

  Scenario: Confirm transaction deletion removes transaction
    Given I am logged in via UI
    And I upload the sample CSV on overview
    And I open the transactions page
    And I confirm the next confirmation dialog
    When I delete the transaction with amount "-52.84 EUR"
    Then I should not see transaction amount "-52.84 EUR"

  Scenario: Cancel delete all data keeps imported data
    Given I am logged in via UI
    And I upload the sample CSV on overview
    And I open the settings page
    And I cancel the next confirmation dialog
    When I trigger delete all data
    And I open the transactions page
    Then I should see transaction amount "-52.84 EUR"

  Scenario: Confirm delete all data clears overview state
    Given I am logged in via UI
    And I upload the sample CSV on overview
    And I open the settings page
    And I confirm the next confirmation dialog
    When I trigger delete all data
    Then I should see text on page "No balance data available."
    And I should see text on page "No transactions yet."

  Scenario: Cancel delete account keeps active session
    Given I am logged in via UI
    And I open the settings page
    And I cancel the next confirmation dialog
    When I trigger delete account
    Then I should still be on "/settings"

  Scenario: Confirm delete account logs out and blocks old login
    Given I am logged in via UI
    And I open the settings page
    And I confirm the next confirmation dialog
    When I trigger delete account
    Then I should still be on "/login"
    When I attempt login with "user@example.com" and "password123"
    Then I should see text on page "Invalid email or password."
