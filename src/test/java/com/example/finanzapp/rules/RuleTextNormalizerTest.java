package com.example.finanzapp.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleTextNormalizerTest {
  private final RuleTextNormalizer normalizer = new RuleTextNormalizer();

  @Test
  void normalizeLowercasesTrimsAndCollapsesWhitespace() {
    String result = normalizer.normalize("  Foo   BAR\tBaz  ");
    assertThat(result).isEqualTo("foo bar baz");
  }

  @Test
  void normalizeRemovesApostrophesAndDiacritics() {
    String result = normalizer.normalize("McDonald’s Café");
    assertThat(result).isEqualTo("mcdonalds cafe");
  }

  @Test
  void normalizeUsesNfkcForCompatibilityCharacters() {
    String result = normalizer.normalize("ＡＢＣ１２３");
    assertThat(result).isEqualTo("abc123");
  }
}
