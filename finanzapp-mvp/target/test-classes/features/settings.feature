Feature: Settings actions
  Scenario: Settings page shows data actions
    Given I am logged in
    When I request "/settings"
    Then I see text "Import CSV"
    And I see text "Upload CSV"
    And I see text "Delete all data"
    And I see text "Delete account"

  Scenario: Update profile redirects to settings
    Given I am logged in
    When I post to "/settings/profile"
    Then I am redirected to "/settings"

  Scenario: Update language redirects to settings
    Given I am logged in
    When I post to "/settings/language"
    Then I am redirected to "/settings"

  Scenario: Delete all data redirects to overview
    Given I am logged in
    When I post to "/settings/delete-all-data"
    Then I am redirected to "/overview"

  Scenario: Delete account redirects to login
    Given I am logged in
    When I post to "/settings/delete-account"
    Then I am redirected to "/login"
