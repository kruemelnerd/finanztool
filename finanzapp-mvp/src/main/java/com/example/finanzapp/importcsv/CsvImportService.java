package com.example.finanzapp.importcsv;

import com.example.finanzapp.balance.BalancePoint;
import com.example.finanzapp.balance.BalanceService;
import com.example.finanzapp.domain.CsvArtifact;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.CsvArtifactRepository;
import com.example.finanzapp.repository.TransactionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvImportService {
  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
  private static final Pattern REFERENCE_PATTERN =
      Pattern.compile("(?i)\\bref\\b\\.?\\s*[:#-]?\\s*([^\\s;,)]+)");

  private final CsvArtifactRepository csvArtifactRepository;
  private final TransactionRepository transactionRepository;
  private final BalanceService balanceService;
  private final CsvParser csvParser = new CsvParser();

  public CsvImportService(
      CsvArtifactRepository csvArtifactRepository,
      TransactionRepository transactionRepository,
      BalanceService balanceService) {
    this.csvArtifactRepository = csvArtifactRepository;
    this.transactionRepository = transactionRepository;
    this.balanceService = balanceService;
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
        duplicateSamples.add(formatDuplicate(transaction));
        continue;
      }
      newTransactions.add(transaction);
    }

    if (!newTransactions.isEmpty()) {
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
    normalized = normalized.replaceAll("^[\\p{Punct}\\s]+", "");
    normalized = normalized.replaceAll("[\\p{Punct}\\s]+$", "");
    return normalized;
  }

  private String extractReferenceToken(String source) {
    if (source == null) {
      return "";
    }
    Matcher matcher = REFERENCE_PATTERN.matcher(source);
    if (!matcher.find()) {
      return "";
    }

    String token = matcher.group(1);
    if (token == null) {
      return "";
    }
    return normalizeReference(token);
  }

  private String formatDuplicate(Transaction transaction) {
    String date = transaction.getBookingDateTime().toLocalDate().toString();
    String amount = formatAmount(transaction.getAmountCents());
    String name = transaction.getPartnerName();
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

  private String formatAmount(long cents) {
    boolean negative = cents < 0;
    long abs = Math.abs(cents);
    long euros = abs / 100;
    long remainder = abs % 100;
    return String.format(Locale.US, "%s%d.%02d EUR", negative ? "-" : "", euros, remainder);
  }
}
