Feature: Transaction list behavior
  Scenario: Pagination splits long transaction lists
    Given I am logged in as "user@example.com"
    And I have twelve transactions for pagination
    When I request "/transactions?page=0"
    Then I see text "TX-12"
    And I do not see text "TX-01"
    And I see text "Page 1 of 2"
    When I request "/transactions?page=1"
    Then I see text "TX-01"
    And I see text "Page 2 of 2"

  Scenario: Resetting filters restores the standard list
    Given I am logged in as "user@example.com"
    And I have transactions for amount filtering
    When I request "/transactions?minAmount=100"
    Then I see text "Large expense"
    And I do not see text "Small expense"
    When I request "/transactions"
    Then I see text "Large expense"
    And I see text "Small expense"

  Scenario: Delete transaction redirects to list
    Given I am logged in
    When I post to "/transactions/42/delete"
    Then I am redirected to "/transactions"
