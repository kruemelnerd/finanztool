package com.example.finanzapp.rules;

import com.example.finanzapp.domain.Rule;
import com.example.finanzapp.domain.RuleMatchField;
import com.example.finanzapp.domain.Transaction;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuleEngine {
  private final RuleTextNormalizer normalizer;

  public RuleEngine(RuleTextNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  public RuleEvaluation evaluate(Transaction transaction, List<Rule> orderedRules) {
    if (orderedRules == null || orderedRules.isEmpty()) {
      return new RuleEvaluation(null, List.of());
    }

    String bookingText = normalizer.normalize(resolveBookingText(transaction));
    String partnerName = normalizer.normalize(transaction.getPartnerName());

    Rule winningRule = null;
    List<Integer> conflictingRuleIds = new ArrayList<>();

    for (Rule rule : orderedRules) {
      if (rule == null || !rule.isActive() || rule.getDeletedAt() != null) {
        continue;
      }

      String needle = normalizer.normalize(rule.getMatchText());
      if (needle.isBlank()) {
        continue;
      }

      if (!matches(rule.getMatchField(), needle, bookingText, partnerName)) {
        continue;
      }

      if (winningRule == null) {
        winningRule = rule;
        continue;
      }

      if (rule.getId() != null) {
        conflictingRuleIds.add(rule.getId());
      }
    }

    return new RuleEvaluation(winningRule, List.copyOf(conflictingRuleIds));
  }

  private boolean matches(
      RuleMatchField field,
      String needle,
      String bookingText,
      String partnerName) {
    RuleMatchField matchField = field == null ? RuleMatchField.BOTH : field;
    return switch (matchField) {
      case BOOKING_TEXT -> bookingText.contains(needle);
      case PARTNER_NAME -> partnerName.contains(needle);
      case BOTH -> bookingText.contains(needle) || partnerName.contains(needle);
    };
  }

  private String resolveBookingText(Transaction transaction) {
    String rawBooking = transaction.getRawBookingText();
    String purpose = transaction.getPurposeText();
    if (rawBooking == null && purpose == null) {
      return "";
    }
    if (rawBooking == null) {
      return purpose;
    }
    if (purpose == null) {
      return rawBooking;
    }
    return rawBooking + " " + purpose;
  }

  public record RuleEvaluation(Rule winningRule, List<Integer> conflictRuleIds) {}
}
