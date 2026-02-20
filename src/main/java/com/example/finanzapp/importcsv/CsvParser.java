package com.example.finanzapp.importcsv;

import com.example.finanzapp.domain.Transaction;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CsvParser {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
  private static final char CSV_DELIMITER = ';';
  private static final String HEADER_BOOKING_DAY = "Buchungstag";
  private static final String HEADER_VALUE_DATE = "Wertstellung (Valuta)";
  private static final String HEADER_TRANSACTION_TYPE = "Vorgang";
  private static final String HEADER_BOOKING_TEXT = "Buchungstext";
  private static final String HEADER_AMOUNT = "Umsatz in EUR";

  public CsvParsingResult parse(byte[] bytes) {
    String content = decodeContent(bytes);
    List<String> lines = content.lines().toList();
    int headerIndex = findHeaderIndex(lines);
    if (headerIndex < 0) {
      throw new CsvImportException("CSV header not found");
    }

    BalanceMeta balances = parseBalances(lines);
    String dataSection = String.join("\n", lines.subList(headerIndex, lines.size()));
    List<Transaction> transactions = parseTransactions(dataSection);

    long transactionSum = transactions.stream()
        .mapToLong(Transaction::getAmountCents)
        .sum();
    Long startBalance = deriveStartBalance(balances.oldBalanceCents(), balances.newBalanceCents(), transactionSum);
    Long currentBalance = deriveCurrentBalance(balances.oldBalanceCents(), balances.newBalanceCents(), transactionSum);
    return new CsvParsingResult(startBalance, currentBalance, transactions);
  }

  private String decodeContent(byte[] bytes) {
    byte[] withoutBom = stripUtf8Bom(bytes);
    String utf8 = decodeStrict(withoutBom, StandardCharsets.UTF_8);
    if (utf8 != null) {
      return utf8;
    }
    return new String(withoutBom, WINDOWS_1252);
  }

  private byte[] stripUtf8Bom(byte[] bytes) {
    if (bytes != null
        && bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xEF
        && (bytes[1] & 0xFF) == 0xBB
        && (bytes[2] & 0xFF) == 0xBF) {
      byte[] withoutBom = new byte[bytes.length - 3];
      System.arraycopy(bytes, 3, withoutBom, 0, withoutBom.length);
      return withoutBom;
    }
    return bytes;
  }

  private String decodeStrict(byte[] bytes, Charset charset) {
    CharsetDecoder decoder = charset.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPORT);
    decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
      return decoded.toString();
    } catch (CharacterCodingException ex) {
      return null;
    }
  }

  private int findHeaderIndex(List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.contains(HEADER_BOOKING_DAY) && line.contains(HEADER_AMOUNT)) {
        return i;
      }
    }
    return -1;
  }

  private BalanceMeta parseBalances(List<String> metaLines) {
    Long oldBalance = null;
    Long newBalance = null;
    for (String line : metaLines) {
      String trimmed = line == null ? "" : line.trim();
      if (isBalanceLine(trimmed, "Alter Kontostand")) {
        Long parsed = parseBalanceLine(line);
        if (parsed != null) {
          oldBalance = parsed;
        }
      }
      if (isBalanceLine(trimmed, "Neuer Kontostand")) {
        Long parsed = parseBalanceLine(line);
        if (parsed != null) {
          newBalance = parsed;
        }
      }
    }
    return new BalanceMeta(oldBalance, newBalance);
  }

  private boolean isBalanceLine(String line, String key) {
    return line.startsWith(key) || line.startsWith('"' + key + '"');
  }

  private Long deriveStartBalance(Long oldBalance, Long newBalance, long transactionSum) {
    if (oldBalance != null) {
      return oldBalance;
    }
    if (newBalance != null) {
      return newBalance - transactionSum;
    }
    return null;
  }

  private Long deriveCurrentBalance(Long oldBalance, Long newBalance, long transactionSum) {
    if (newBalance != null) {
      return newBalance;
    }
    if (oldBalance != null) {
      return oldBalance + transactionSum;
    }
    return null;
  }

  private Long parseBalanceLine(String line) {
    try (CSVParser parser = CSVParser.parse(
        new StringReader(line),
        CSVFormat.DEFAULT.builder().setDelimiter(CSV_DELIMITER).build())) {
      for (CSVRecord record : parser) {
        if (record.size() > 1) {
          String raw = clean(record.get(1));
          if (raw != null && !raw.isBlank()) {
            try {
              return parseAmountToCents(raw);
            } catch (CsvImportException ex) {
              return null;
            }
          }
        }
      }
    } catch (IOException | RuntimeException ex) {
      return null;
    }
    return null;
  }

  private List<Transaction> parseTransactions(String dataSection) {
    try (CSVParser parser = CSVParser.parse(
        new StringReader(dataSection),
        CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setAllowMissingColumnNames(true)
            .setDelimiter(CSV_DELIMITER)
            .build())) {
      List<Transaction> transactions = new ArrayList<>();
      for (CSVRecord record : parser) {
        if (isBlankRecord(record)) {
          continue;
        }
        if (isTrailingMetaRecord(record)) {
          continue;
        }
        ensureMinimumTransactionColumns(record);

        String transactionType = getColumnValue(record, HEADER_TRANSACTION_TYPE);
        String rawBookingText = getColumnValue(record, HEADER_BOOKING_TEXT);
        String amountRaw = getColumnValue(record, HEADER_AMOUNT);
        BookingTextParts bookingTextParts = parseBookingTextParts(rawBookingText);

        LocalDate bookingDate = tryParseDate(getColumnValue(record, HEADER_BOOKING_DAY));
        if (bookingDate == null) {
          if (shouldSkipRecord(transactionType, rawBookingText, amountRaw)) {
            continue;
          }
          throw new CsvImportException(
              "Invalid Buchungstag in row " + record.getRecordNumber() + ": " + record);
        }

        Transaction transaction = new Transaction();
        LocalDateTime bookingDateTime = LocalDateTime.of(bookingDate, LocalTime.MIDNIGHT);
        transaction.setBookingDateTime(bookingDateTime);
        transaction.setValueDate(
            parseOptionalDate(getColumnValue(record, HEADER_VALUE_DATE), record.getRecordNumber()));
        transaction.setTransactionType(transactionType);
        transaction.setRawBookingText(rawBookingText);
        transaction.setPayerName(bookingTextParts.payerName());
        transaction.setBookingText(bookingTextParts.bookingText());
        transaction.setCardNumber(bookingTextParts.cardNumber());
        transaction.setCardPaymentText(bookingTextParts.cardPaymentText());
        transaction.setReferenceText(bookingTextParts.referenceText());
        transaction.setPartnerName(determinePartnerName(bookingTextParts, rawBookingText, transaction.getTransactionType()));
        transaction.setPurposeText(determinePurposeText(bookingTextParts, rawBookingText));

        transaction.setAmountCents(parseAmountToCents(amountRaw));
        transactions.add(transaction);
      }
      return transactions;
    } catch (IOException ex) {
      throw new CsvImportException("CSV parsing failed", ex);
    }
  }

  private void ensureMinimumTransactionColumns(CSVRecord record) {
    if (record.size() >= 5) {
      return;
    }
    throw new CsvImportException(
        "Invalid CSV row " + record.getRecordNumber()
            + ": expected at least 5 columns ("
            + HEADER_BOOKING_DAY + ";"
            + HEADER_VALUE_DATE + ";"
            + HEADER_TRANSACTION_TYPE + ";"
            + HEADER_BOOKING_TEXT + ";"
            + HEADER_AMOUNT + ") but got "
            + record.size() + " values: " + record);
  }

  private boolean isBlankRecord(CSVRecord record) {
    for (int i = 0; i < record.size(); i++) {
      String value = record.get(i);
      if (value != null && !value.isBlank()) {
        return false;
      }
    }
    return true;
  }

  private boolean isTrailingMetaRecord(CSVRecord record) {
    if (record.size() == 0) {
      return true;
    }
    String firstValue = clean(record.get(0));
    if (firstValue == null || firstValue.isBlank()) {
      return false;
    }
    String normalized = firstValue.toLowerCase(Locale.ROOT);
    return normalized.startsWith("alter kontostand")
        || normalized.startsWith("neuer kontostand")
        || normalized.startsWith("umsätze girokonto")
        || normalized.startsWith("umsaetze girokonto")
        || normalized.startsWith("zeitraum")
        || normalized.equals(HEADER_BOOKING_DAY.toLowerCase(Locale.ROOT));
  }

  private String getColumnValue(CSVRecord record, String headerName) {
    if (!record.isMapped(headerName) || !record.isSet(headerName)) {
      return null;
    }
    return clean(record.get(headerName));
  }

  private LocalDate tryParseDate(String raw) {
    String value = clean(raw);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value, DATE_FORMAT);
    } catch (DateTimeParseException ex) {
      try {
        return LocalDate.parse(value);
      } catch (DateTimeParseException second) {
        return null;
      }
    }
  }

  private LocalDate parseOptionalDate(String raw, long recordNumber) {
    String value = clean(raw);
    if (value == null || value.isBlank()) {
      return null;
    }
    LocalDate parsed = tryParseDate(value);
    if (parsed == null) {
      throw new CsvImportException(
          "Invalid Wertstellung (Valuta) in row " + recordNumber);
    }
    return parsed;
  }

  private boolean shouldSkipRecord(String transactionType, String rawBookingText, String amountRaw) {
    return isBlank(transactionType) && isBlank(rawBookingText) && isBlank(amountRaw);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String determinePartnerName(BookingTextParts parts, String rawBookingText, String fallback) {
    if (hasText(parts.payerName())) {
      return parts.payerName();
    }
    String prefixBeforeBookingText = extractPrefixBeforeMarker(rawBookingText, "Buchungstext:");
    if (hasText(prefixBeforeBookingText)) {
      return prefixBeforeBookingText;
    }
    if (fallback != null && !fallback.isBlank()) {
      return fallback.trim();
    }
    return "Unknown";
  }

  private String determinePurposeText(BookingTextParts parts, String rawBookingText) {
    if (hasText(parts.bookingText())) {
      return parts.bookingText();
    }
    if (rawBookingText == null || rawBookingText.isBlank()) {
      return "";
    }
    return rawBookingText.trim();
  }

  private BookingTextParts parseBookingTextParts(String rawBookingText) {
    if (rawBookingText == null || rawBookingText.isBlank()) {
      return new BookingTextParts(null, null, null, null, null);
    }
    String payerName = extractBetweenMarkers(rawBookingText, "Auftraggeber:", "Buchungstext:");

    String bookingTextRaw = extractAfter(rawBookingText, "Buchungstext:");
    String bookingText = null;
    if (bookingTextRaw != null) {
      bookingText = trimAtMarker(bookingTextRaw, "Karte Nr.");
      bookingText = trimAtMarker(bookingText, "Ref.");
    }

    String cardNumberRaw = extractAfter(rawBookingText, "Karte Nr.");
    String cardNumber = null;
    if (cardNumberRaw != null) {
      cardNumber = trimAtMarker(cardNumberRaw, "Kartenzahlung");
      cardNumber = trimAtMarker(cardNumber, "Ref.");
    }

    String cardPaymentRaw = extractAfter(rawBookingText, "Kartenzahlung");
    String cardPaymentText = cardPaymentRaw == null ? null : trimAtMarker(cardPaymentRaw, "Ref.");
    String referenceText = extractAfter(rawBookingText, "Ref.");

    return new BookingTextParts(
        blankToNull(payerName),
        blankToNull(bookingText),
        blankToNull(cardNumber),
        blankToNull(cardPaymentText),
        blankToNull(referenceText));
  }

  private String extractBetweenMarkers(String raw, String startMarker, String endMarker) {
    String afterStart = extractAfter(raw, startMarker);
    if (afterStart == null) {
      return null;
    }
    return trimAtMarker(afterStart, endMarker);
  }

  private String extractPrefixBeforeMarker(String text, String marker) {
    if (text == null) {
      return "";
    }
    int index = indexOfIgnoreCase(text, marker);
    if (index <= 0) {
      return "";
    }
    String prefix = text.substring(0, index).trim();
    String withoutMarker = extractAfter(prefix, "Auftraggeber:");
    if (withoutMarker != null) {
      return withoutMarker;
    }
    return prefix;
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String extractAfter(String text, String marker) {
    if (text == null) {
      return null;
    }
    int index = indexOfIgnoreCase(text, marker);
    if (index < 0) {
      return null;
    }
    return text.substring(index + marker.length()).trim();
  }

  private String trimAtMarker(String text, String marker) {
    if (text == null) {
      return "";
    }
    int index = indexOfIgnoreCase(text, marker);
    if (index < 0) {
      return text.trim();
    }
    return text.substring(0, index).trim();
  }

  private int indexOfIgnoreCase(String text, String marker) {
    return text.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
  }

  private long parseAmountToCents(String raw) {
    if (raw == null) {
      return 0L;
    }
    String cleaned = normalizeAmount(raw);
    if (cleaned.isBlank()) {
      return 0L;
    }
    try {
      BigDecimal value = new BigDecimal(cleaned);
      return value.movePointRight(2).setScale(0).longValue();
    } catch (NumberFormatException ex) {
      throw new CsvImportException("Invalid amount format: " + raw, ex);
    }
  }

  private String normalizeAmount(String raw) {
    String value = raw
        .replace("\"", "")
        .replace("EUR", "")
        .replace("€", "")
        .replace("\u00A0", "")
        .replace(" ", "")
        .trim();
    if (value.isBlank()) {
      return "";
    }
    value = value.replaceAll("[^0-9,.-]", "");

    if (value.contains(",") && value.contains(".")) {
      value = value.replace(".", "");
    }
    return value.replace(',', '.');
  }

  private String clean(String value) {
    if (value == null) {
      return null;
    }
    return stripQuotes(value).trim();
  }

  private String stripQuotes(String value) {
    String trimmed = value.trim();
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private record BalanceMeta(Long oldBalanceCents, Long newBalanceCents) {}

  private record BookingTextParts(
      String payerName,
      String bookingText,
      String cardNumber,
      String cardPaymentText,
      String referenceText) {}
}
