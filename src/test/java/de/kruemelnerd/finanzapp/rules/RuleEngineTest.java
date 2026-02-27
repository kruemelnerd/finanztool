package de.kruemelnerd.finanzapp.rules;

import static org.assertj.core.api.Assertions.assertThat;

import de.kruemelnerd.finanzapp.domain.Category;
import de.kruemelnerd.finanzapp.domain.Rule;
import de.kruemelnerd.finanzapp.domain.RuleMatchField;
import de.kruemelnerd.finanzapp.domain.Transaction;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEngineTest {
  private final RuleEngine ruleEngine = new RuleEngine(new RuleTextNormalizer());

  @Test
  void evaluateUsesFirstMatchingRuleAndCapturesFurtherRuleIds() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setPartnerName("McDonald’s Berlin");
    transaction.setPurposeText("Kauf");

    Category firstCategory = categoryWithId(1);
    Category secondCategory = categoryWithId(2);
    Rule first = ruleWith(10, "FastFood", "mcdonalds", RuleMatchField.PARTNER_NAME, firstCategory);
    Rule second = ruleWith(20, "Generic Name", "donald", RuleMatchField.BOTH, secondCategory);

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(first, second));

    assertThat(evaluation.winningRule()).isEqualTo(first);
    assertThat(evaluation.conflictRuleIds()).containsExactly(20);
  }

  @Test
  void evaluateMatchesBookingTextAndPartnerNameWhenBothIsSelected() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setPartnerName("Local Store");
    transaction.setRawBookingText("Buchungstext: InterSport Hamburg");

    Rule both = ruleWith(11, "Sport", "intersport", RuleMatchField.BOTH, categoryWithId(3));

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(both));

    assertThat(evaluation.winningRule()).isEqualTo(both);
    assertThat(evaluation.conflictRuleIds()).isEmpty();
  }

  @Test
  void evaluateReturnsNoWinnerWhenNoRuleMatches() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setPartnerName("Unknown");
    transaction.setPurposeText("Text");

    Rule rule = ruleWith(12, "Sport", "intersport", RuleMatchField.BOTH, categoryWithId(4));

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(rule));

    assertThat(evaluation.winningRule()).isNull();
    assertThat(evaluation.conflictRuleIds()).isEmpty();
  }

  @Test
  void evaluateDoesNotFlagConflictWhenMatchesComeFromSameCategory() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setPartnerName("Burger King Innenstadt");
    transaction.setPurposeText("Lunch");

    Category fastFood = categoryWithId(8);
    Rule first = ruleWith(31, "FastFood A", "burger", RuleMatchField.BOTH, fastFood);
    Rule second = ruleWith(32, "FastFood B", "king", RuleMatchField.PARTNER_NAME, fastFood);

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(first, second));

    assertThat(evaluation.winningRule()).isEqualTo(first);
    assertThat(evaluation.conflictRuleIds()).isEmpty();
  }

  private Rule ruleWith(int id, String name, String matchText, RuleMatchField field, Category category) throws Exception {
    Rule rule = new Rule();
    rule.setName(name);
    rule.setMatchText(matchText);
    rule.setMatchField(field);
    rule.setActive(true);
    rule.setCategory(category);

    Field idField = Rule.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(rule, id);
    return rule;
  }

  private Category categoryWithId(int id) throws Exception {
    Category category = new Category();
    Field idField = Category.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(category, id);
    return category;
  }
}
