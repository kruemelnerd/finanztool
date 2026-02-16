package com.example.finanzapp.transactions;

import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.balance.AccountBalanceService;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class TransactionViewService {
  private static final DateTimeFormatter DATE_FORMAT_EN = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATE_FORMAT_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int MAX_PAGE_SIZE = 100;

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final AccountBalanceService accountBalanceService;

  public TransactionViewService(
      TransactionRepository transactionRepository,
      UserRepository userRepository,
      AccountBalanceService accountBalanceService) {
    this.transactionRepository = transactionRepository;
    this.userRepository = userRepository;
    this.accountBalanceService = accountBalanceService;
  }

  public List<TransactionRow> loadTransactions(
      UserDetails userDetails,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String exactName,
      String exactPurpose) {
    LoadedTransactions loaded = loadFilteredTransactions(
        userDetails,
        minAmount,
        maxAmount,
        exactName,
        exactPurpose);
    return loaded.transactions().stream()
        .map(tx -> toRow(tx, loaded.locale()))
        .toList();
  }

  public TransactionPage loadTransactionsPage(
      UserDetails userDetails,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String nameContains,
      String purposeContains,
      Integer page,
      Integer pageSize) {
    LoadedTransactions loaded = loadFilteredTransactions(
        userDetails,
        minAmount,
        maxAmount,
        nameContains,
        purposeContains);

    int safePageSize = normalizePageSize(pageSize);
    int totalItems = loaded.transactions().size();
    int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safePageSize));
    int safePage = normalizePage(page, totalPages);

    int fromIndex = safePage * safePageSize;
    int toIndex = Math.min(fromIndex + safePageSize, totalItems);
    List<TransactionRow> rows = loaded.transactions().subList(fromIndex, toIndex).stream()
        .map(tx -> toRow(tx, loaded.locale()))
        .toList();

    return new TransactionPage(rows, safePage, safePageSize, totalPages, totalItems);
  }

  public List<TransactionRow> loadRecent(UserDetails userDetails, int limit) {
    List<TransactionRow> rows = loadTransactions(userDetails, null, null, null, null);
    if (rows.size() <= limit) {
      return rows;
    }
    return rows.subList(0, limit);
  }

  public String loadCurrentBalanceLabel(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return null;
    }
    Locale locale = resolveLocale(user.get());
    return accountBalanceService.computeCurrentBalanceCents(user.get())
        .map(cents -> formatAmount(cents, locale))
        .orElse(null);
  }

  public boolean softDeleteTransaction(UserDetails userDetails, Integer id) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty() || id == null) {
      return false;
    }
    int updated = transactionRepository.softDeleteByIdAndUser(id, user.get(), Instant.now());
    return updated > 0;
  }

  private TransactionRow toRow(Transaction transaction, Locale locale) {
    String date = resolveDateFormatter(locale).format(transaction.getBookingDateTime());
    String time = TIME_FORMAT.format(transaction.getBookingDateTime());
    String name = sanitizeNameForDisplay(transaction.getPartnerName());
    String purpose = sanitizePurposeForDisplay(transaction.getPurposeText());
    String amount = formatAmount(transaction.getAmountCents(), locale);
    return new TransactionRow(
        transaction.getId(),
        name,
        purpose,
        date,
        time,
        transaction.getStatus(),
        amount);
  }

  private String sanitizeNameForDisplay(String partnerName) {
    if (partnerName == null) {
      return "";
    }
    String value = partnerName.trim();
    if (value.isEmpty()) {
      return "";
    }
    int bookingTextIndex = indexOfIgnoreCase(value, "Buchungstext:");
    if (bookingTextIndex >= 0) {
      value = value.substring(0, bookingTextIndex).trim();
    }
    int cardIndex = indexOfIgnoreCase(value, "Karte Nr.");
    if (cardIndex >= 0) {
      value = value.substring(0, cardIndex).trim();
    }
    int referenceIndex = indexOfIgnoreCase(value, "Ref.");
    if (referenceIndex >= 0) {
      value = value.substring(0, referenceIndex).trim();
    }
    return value;
  }

  private String sanitizePurposeForDisplay(String purposeText) {
    if (purposeText == null) {
      return "";
    }
    String value = purposeText.trim();
    if (value.isEmpty()) {
      return "";
    }
    int bookingTextIndex = indexOfIgnoreCase(value, "Buchungstext:");
    if (bookingTextIndex >= 0) {
      value = value.substring(bookingTextIndex + "Buchungstext:".length()).trim();
    }
    int cardIndex = indexOfIgnoreCase(value, "Karte Nr.");
    if (cardIndex >= 0) {
      value = value.substring(0, cardIndex).trim();
    }
    return value;
  }

  private int indexOfIgnoreCase(String source, String marker) {
    return source.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
  }

  private long toCents(BigDecimal amount) {
    return amount.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
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
    if ("DE".equalsIgnoreCase(user.getLanguage())) {
      return Locale.GERMANY;
    }
    return Locale.ENGLISH;
  }

  private Optional<User> resolveUser(UserDetails userDetails) {
    if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(userDetails.getUsername());
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private boolean containsIgnoreCase(String source, String normalizedNeedle) {
    if (source == null) {
      return false;
    }
    return source.toLowerCase(Locale.ROOT).contains(normalizedNeedle);
  }

  private LoadedTransactions loadFilteredTransactions(
      UserDetails userDetails,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String nameContains,
      String purposeContains) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return new LoadedTransactions(List.of(), Locale.ENGLISH);
    }

    List<Transaction> transactions =
        transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user.get());
    Locale locale = resolveLocale(user.get());

    if (minAmount != null) {
      long minCents = Math.abs(toCents(minAmount));
      transactions = transactions.stream()
          .filter(tx -> Math.abs(tx.getAmountCents()) >= minCents)
          .toList();
    }
    if (maxAmount != null) {
      long maxCents = Math.abs(toCents(maxAmount));
      transactions = transactions.stream()
          .filter(tx -> Math.abs(tx.getAmountCents()) <= maxCents)
          .toList();
    }
    if (hasText(nameContains)) {
      String normalized = nameContains.trim().toLowerCase(Locale.ROOT);
      transactions = transactions.stream()
          .filter(tx -> containsIgnoreCase(tx.getPartnerName(), normalized))
          .toList();
    }
    if (hasText(purposeContains)) {
      String normalized = purposeContains.trim().toLowerCase(Locale.ROOT);
      transactions = transactions.stream()
          .filter(tx -> containsIgnoreCase(tx.getPurposeText(), normalized))
          .toList();
    }
    return new LoadedTransactions(transactions, locale);
  }

  private int normalizePage(Integer page, int totalPages) {
    if (page == null || page < 0) {
      return 0;
    }
    return Math.min(page, totalPages - 1);
  }

  private int normalizePageSize(Integer pageSize) {
    if (pageSize == null || pageSize <= 0) {
      return 10;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private record LoadedTransactions(List<Transaction> transactions, Locale locale) {}
}
