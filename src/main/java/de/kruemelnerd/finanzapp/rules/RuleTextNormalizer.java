package de.kruemelnerd.finanzapp.rules;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RuleTextNormalizer {
  private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private static final Pattern APOSTROPHES = Pattern.compile("['`´\u2018\u2019\u02BC]");

  public String normalize(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
    normalized = normalized.toLowerCase(Locale.ROOT).trim();
    normalized = APOSTROPHES.matcher(normalized).replaceAll("");
    normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
    normalized = DIACRITICS.matcher(normalized).replaceAll("");
    normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ");
    return normalized;
  }
}
