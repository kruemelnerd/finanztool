package de.kruemelnerd.finanzapp.importcsv;

import de.kruemelnerd.finanzapp.balance.BalancePoint;
import de.kruemelnerd.finanzapp.balance.BalanceService;
import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.rules.CategoryAssignmentService;
import de.kruemelnerd.finanzapp.repository.CsvArtifactRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvImportService {
  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
  private static final DateTimeFormatter DATE_FORMAT_EN = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATE_FORMAT_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final CsvArtifactRepository csvArtifactRepository;
  private final TransactionRepository transactionRepository;
  private final BalanceService balanceService;
  private final CategoryAssignmentService categoryAssignmentService;
  private final CsvParser csvParser = new CsvParser();

  public CsvImportService(
      CsvArtifactRepository csvArtifactRepository,
      TransactionRepository transactionRepository,
      BalanceService balanceService,
      CategoryAssignmentService categoryAssignmentService) {
    this.csvArtifactRepository = csvArtifactRepository;
    this.transactionRepository = transactionRepository;
    this.balanceService = balanceService;
    this.categoryAssignmentService = categoryAssignmentService;
  }

  @Transactional
  public CsvImportResult importCsv(
      User user,
      String originalFileName,
      String contentType,
      byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new CsvImportException("CSV file is empty");
    }
    if (bytes.length > MAX_SIZE_BYTES) {
      throw new CsvImportException("CSV exceeds 10MB limit");
    }

    CsvArtifact artifact = new CsvArtifact();
    artifact.setUser(user);
    artifact.setOriginalFileName(originalFileName == null ? "import.csv" : originalFileName);
    artifact.setContentType(contentType);
    artifact.setBytes(bytes);
    artifact.setSizeBytes(bytes.length);
    csvArtifactRepository.save(artifact);

    CsvParsingResult parsed = csvParser.parse(bytes);
    Locale locale = resolveLocale(user);

    List<Transaction> parsedTransactions = parsed.transactions();
    List<Transaction> existing =
        transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user);
    Set<String> existingKeys = new HashSet<>();
    for (Transaction tx : existing) {
      existingKeys.add(buildKey(tx));
    }

    List<Transaction> newTransactions = new ArrayList<>();
    List<String> duplicateSamples = new ArrayList<>();
    Set<String> seenInImport = new HashSet<>();

    for (Transaction transaction : parsedTransactions) {
      transaction.setUser(user);
      String key = buildKey(transaction);
      if (existingKeys.contains(key) || !seenInImport.add(key)) {
        duplicateSamples.add(formatDuplicate(transaction, locale));
        continue;
      }
      newTransactions.add(transaction);
    }

    if (!newTransactions.isEmpty()) {
      categoryAssignmentService.assignForImport(user, newTransactions);
      transactionRepository.saveAll(newTransactions);
    }

    if (parsed.startBalanceCents() != null) {
      List<Transaction> allActive =
          transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user);
      List<BalancePoint> points = balanceService.computeLast30Days(
          parsed.startBalanceCents(), allActive);
      balanceService.materializeLast30Days(user, points);
    }
    int duplicateCount = parsedTransactions.size() - newTransactions.size();
    return new CsvImportResult(newTransactions.size(), duplicateCount, duplicateSamples);
  }

  private String buildKey(Transaction transaction) {
    String reference = resolveReference(transaction);
    if (!reference.isBlank()) {
      return "ref|" + reference;
    }

    String date = transaction.getBookingDateTime().toLocalDate().toString();
    String amount = Long.toString(transaction.getAmountCents());
    String name = normalize(transaction.getPartnerName());
    String purpose = normalizePurpose(transaction.getPurposeText());
    String type = normalize(transaction.getTransactionType());
    return String.join("|", date, amount, name, purpose, type);
  }

  private String resolveReference(Transaction transaction) {
    String reference = normalizeReference(transaction.getReferenceText());
    if (!reference.isBlank()) {
      return reference;
    }

    reference = extractReferenceToken(transaction.getRawBookingText());
    if (!reference.isBlank()) {
      return reference;
    }

    return extractReferenceToken(transaction.getPurposeText());
  }

  private String normalizeReference(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return "";
    }

    int start = 0;
    int end = normalized.length() - 1;
    while (start <= end && isTrimmedReferenceBoundary(normalized.charAt(start))) {
      start++;
    }
    while (end >= start && isTrimmedReferenceBoundary(normalized.charAt(end))) {
      end--;
    }

    if (start > end) {
      return "";
    }
    return normalized.substring(start, end + 1);
  }

  private String extractReferenceToken(String source) {
    if (source == null || source.isBlank()) {
      return "";
    }

    String lower = source.toLowerCase(Locale.ROOT);
    int fromIndex = 0;
    while (fromIndex < lower.length()) {
      int markerStart = lower.indexOf("ref", fromIndex);
      if (markerStart < 0) {
        return "";
      }
      fromIndex = markerStart + 3;

      if (!isStandaloneRefMarker(lower, markerStart)) {
        continue;
      }

      int tokenStart = skipReferencePrefixSeparators(source, markerStart + 3);
      int tokenEnd = tokenStart;
      while (tokenEnd < source.length() && !isReferenceTokenTerminator(source.charAt(tokenEnd))) {
        tokenEnd++;
      }

      String token = normalizeReference(source.substring(tokenStart, tokenEnd));
      if (!token.isBlank()) {
        return token;
      }
    }

    return "";
  }

  private boolean isTrimmedReferenceBoundary(char value) {
    return Character.isWhitespace(value) || isAsciiPunctuation(value);
  }

  private boolean isAsciiPunctuation(char value) {
    return (value >= '!' && value <= '/')
        || (value >= ':' && value <= '@')
        || (value >= '[' && value <= '`')
        || (value >= '{' && value <= '~');
  }

  private boolean isStandaloneRefMarker(String source, int markerStart) {
    int before = markerStart - 1;
    if (before >= 0 && isWordCharacter(source.charAt(before))) {
      return false;
    }

    int after = markerStart + 3;
    return after >= source.length() || !isWordCharacter(source.charAt(after));
  }

  private boolean isWordCharacter(char value) {
    return Character.isLetterOrDigit(value) || value == '_';
  }

  private int skipReferencePrefixSeparators(String source, int index) {
    int current = index;

    if (current < source.length() && source.charAt(current) == '.') {
      current++;
    }
    while (current < source.length() && Character.isWhitespace(source.charAt(current))) {
      current++;
    }
    if (current < source.length()) {
      char value = source.charAt(current);
      if (value == ':' || value == '#' || value == '-') {
        current++;
      }
    }
    while (current < source.length() && Character.isWhitespace(source.charAt(current))) {
      current++;
    }

    return current;
  }

  private boolean isReferenceTokenTerminator(char value) {
    return Character.isWhitespace(value) || value == ';' || value == ',' || value == ')';
  }

  private String formatDuplicate(Transaction transaction, Locale locale) {
    String date = resolveDateFormatter(locale).format(transaction.getBookingDateTime().toLocalDate());
    String amount = formatAmount(transaction.getAmountCents(), locale);
    String name = transaction.getPartnerName() == null ? "" : transaction.getPartnerName();
    return date + " - " + name + " - " + amount;
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizePurpose(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim();
    int bookingTextIndex = indexOfIgnoreCase(normalized, "Buchungstext:");
    if (bookingTextIndex >= 0) {
      normalized = normalized.substring(bookingTextIndex + "Buchungstext:".length()).trim();
    }
    int cardIndex = indexOfIgnoreCase(normalized, "Karte Nr.");
    if (cardIndex >= 0) {
      normalized = normalized.substring(0, cardIndex).trim();
    }
    int referenceIndex = indexOfIgnoreCase(normalized, "Ref.");
    if (referenceIndex >= 0) {
      normalized = normalized.substring(0, referenceIndex).trim();
    }
    return normalized.toLowerCase(Locale.ROOT);
  }

  private int indexOfIgnoreCase(String source, String marker) {
    return source.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
  }

  private String formatAmount(long cents, Locale locale) {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
    DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
    format.setGroupingUsed(true);
    return format.format(cents / 100.0d) + " EUR";
  }

  private DateTimeFormatter resolveDateFormatter(Locale locale) {
    if (Locale.GERMAN.getLanguage().equals(locale.getLanguage())) {
      return DATE_FORMAT_DE;
    }
    return DATE_FORMAT_EN;
  }

  private Locale resolveLocale(User user) {
    if (user != null && "DE".equalsIgnoreCase(user.getLanguage())) {
      return Locale.GERMANY;
    }
    return Locale.ENGLISH;
  }
}
