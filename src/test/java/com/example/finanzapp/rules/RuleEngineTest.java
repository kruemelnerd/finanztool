package com.example.finanzapp.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finanzapp.domain.Rule;
import com.example.finanzapp.domain.RuleMatchField;
import com.example.finanzapp.domain.Transaction;
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

    Rule first = ruleWith(10, "FastFood", "mcdonalds", RuleMatchField.PARTNER_NAME);
    Rule second = ruleWith(20, "Generic Name", "donald", RuleMatchField.BOTH);

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(first, second));

    assertThat(evaluation.winningRule()).isEqualTo(first);
    assertThat(evaluation.conflictRuleIds()).containsExactly(20);
  }

  @Test
  void evaluateMatchesBookingTextAndPartnerNameWhenBothIsSelected() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setPartnerName("Local Store");
    transaction.setRawBookingText("Buchungstext: InterSport Hamburg");

    Rule both = ruleWith(11, "Sport", "intersport", RuleMatchField.BOTH);

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(both));

    assertThat(evaluation.winningRule()).isEqualTo(both);
    assertThat(evaluation.conflictRuleIds()).isEmpty();
  }

  @Test
  void evaluateReturnsNoWinnerWhenNoRuleMatches() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setPartnerName("Unknown");
    transaction.setPurposeText("Text");

    Rule rule = ruleWith(12, "Sport", "intersport", RuleMatchField.BOTH);

    RuleEngine.RuleEvaluation evaluation = ruleEngine.evaluate(transaction, List.of(rule));

    assertThat(evaluation.winningRule()).isNull();
    assertThat(evaluation.conflictRuleIds()).isEmpty();
  }

  private Rule ruleWith(int id, String name, String matchText, RuleMatchField field) throws Exception {
    Rule rule = new Rule();
    rule.setName(name);
    rule.setMatchText(matchText);
    rule.setMatchField(field);
    rule.setActive(true);

    Field idField = Rule.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(rule, id);
    return rule;
  }
}
