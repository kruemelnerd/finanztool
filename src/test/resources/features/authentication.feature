Feature: Authentication and protected access
  Scenario: Login page is accessible
    Given I am not logged in
    When I request "/login"
    Then I see text "Login"

  Scenario: Register page is accessible
    Given I am not logged in
    When I request "/register"
    Then I see text "Create account"

  Scenario: Registration with valid data creates a user and logs in
    Given I am on the registration page
    When I register with email "new-user@example.com" and password "password123"
    And I submit the registration form
    Then a user account exists for "new-user@example.com"
    And I am authenticated
    And I am redirected to "/overview"

  Scenario: Registration with duplicate email is rejected
    Given an account exists for "user@example.com"
    And I am on the registration page
    When I register with email "user@example.com" and password "password123"
    And I submit the registration form
    Then I see text "Email already in use."

  Scenario: Registration with weak password is rejected
    Given I have no account with email "weak@example.com"
    And I am on the registration page
    When I register with email "weak@example.com" and password "short"
    And I submit the registration form
    Then I see text "Password must be at least 8 characters."
    And no user account exists for "weak@example.com"

  Scenario: Login with correct credentials redirects to overview
    Given I have a user account with email "user@example.com" and password "password123"
    And I am not logged in
    When I log in with email "user@example.com" and password "password123"
    Then I am redirected to "/overview"

  Scenario: Login with wrong password is rejected
    Given I have a user account with email "user@example.com" and password "password123"
    And I am not logged in
    When I log in with email "user@example.com" and password "wrong-password"
    Then I am redirected to login with an auth error
    And I remain logged out

  Scenario: Unauthenticated user is redirected to login
    Given I am not logged in
    When I request "/overview"
    Then I am redirected to login

  Scenario: Authenticated user can open dashboard
    Given I am logged in
    When I request "/overview"
    Then I see text "Current balance"

  Scenario: Logout ends the session
    Given I am logged in
    When I post to "/logout"
    Then I am redirected to "/login"
