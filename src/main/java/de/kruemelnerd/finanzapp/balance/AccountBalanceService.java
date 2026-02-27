package de.kruemelnerd.finanzapp.balance;

import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.importcsv.CsvParser;
import de.kruemelnerd.finanzapp.importcsv.CsvParsingResult;
import de.kruemelnerd.finanzapp.repository.CsvArtifactRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AccountBalanceService {
  private final TransactionRepository transactionRepository;
  private final CsvArtifactRepository csvArtifactRepository;
  private final CsvParser csvParser = new CsvParser();

  public AccountBalanceService(
      TransactionRepository transactionRepository,
      CsvArtifactRepository csvArtifactRepository) {
    this.transactionRepository = transactionRepository;
    this.csvArtifactRepository = csvArtifactRepository;
  }

  public Optional<Long> computeCurrentBalanceCents(User user) {
    if (user == null) {
      return Optional.empty();
    }
    List<Transaction> activeTransactions =
        transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user);
    Optional<BalanceAnchor> anchor = resolveAnchor(user);
    if (anchor.isEmpty()) {
      return Optional.empty();
    }
    long current = balanceAt(LocalDate.now(), anchor.get(), sumByDate(activeTransactions));
    return Optional.of(current);
  }

  public List<BalancePoint> computeRange(User user, LocalDate start, LocalDate end) {
    if (user == null || start == null || end == null || end.isBefore(start)) {
      return List.of();
    }

    List<Transaction> activeTransactions =
        transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user);
    Optional<BalanceAnchor> anchor = resolveAnchor(user);
    if (anchor.isEmpty()) {
      return List.of();
    }

    Map<LocalDate, Long> sumByDate = sumByDate(activeTransactions);
    long running = balanceAt(start, anchor.get(), sumByDate);

    List<BalancePoint> points = new ArrayList<>();
    LocalDate cursor = start;
    while (!cursor.isAfter(end)) {
      if (!cursor.equals(start)) {
        running += sumByDate.getOrDefault(cursor, 0L);
      }
      points.add(new BalancePoint(cursor, running));
      cursor = cursor.plusDays(1);
    }
    return points;
  }

  private Optional<BalanceAnchor> resolveAnchor(User user) {
    List<CsvArtifact> artifacts = csvArtifactRepository.findByUserAndDeletedAtIsNull(user);
    List<BalanceAnchor> transactionAnchors = new ArrayList<>();
    BalanceAnchor latestSnapshotAnchor = null;

    for (CsvArtifact artifact : artifacts) {
      CsvParsingResult parsed = parseSafely(artifact.getBytes());
      if (parsed == null) {
        continue;
      }

      List<Transaction> parsedTransactions = parsed.transactions();
      if (!parsedTransactions.isEmpty()) {
        LocalDate oldestDate = parsedTransactions.stream()
            .map(tx -> tx.getBookingDateTime().toLocalDate())
            .min(LocalDate::compareTo)
            .orElse(null);
        if (oldestDate != null) {
          long sum = parsedTransactions.stream().mapToLong(Transaction::getAmountCents).sum();
          if (parsed.currentBalanceCents() != null) {
            long derivedStart = parsed.currentBalanceCents() - sum;
            transactionAnchors.add(new BalanceAnchor(oldestDate.minusDays(1), derivedStart, 0));
          }
          if (parsed.startBalanceCents() != null) {
            transactionAnchors.add(
                new BalanceAnchor(oldestDate.minusDays(1), parsed.startBalanceCents(), 1));
          }
        }
      } else {
        Long snapshot = parsed.currentBalanceCents() != null
            ? parsed.currentBalanceCents()
            : parsed.startBalanceCents();
        if (snapshot != null) {
          LocalDate uploadedDate = artifact.getUploadedAt().atZone(ZoneId.systemDefault()).toLocalDate();
          BalanceAnchor candidate = new BalanceAnchor(uploadedDate, snapshot, 2);
          if (latestSnapshotAnchor == null || candidate.date().isAfter(latestSnapshotAnchor.date())) {
            latestSnapshotAnchor = candidate;
          }
        }
      }
    }

    if (!transactionAnchors.isEmpty()) {
      return transactionAnchors.stream()
          .sorted((left, right) -> {
            int byDate = right.date().compareTo(left.date());
            if (byDate != 0) {
              return byDate;
            }
            return Integer.compare(left.priority(), right.priority());
          })
          .findFirst();
    }
    return Optional.ofNullable(latestSnapshotAnchor);
  }

  private CsvParsingResult parseSafely(byte[] bytes) {
    try {
      return csvParser.parse(bytes);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private Map<LocalDate, Long> sumByDate(List<Transaction> transactions) {
    Map<LocalDate, Long> sumByDate = new HashMap<>();
    for (Transaction tx : transactions) {
      LocalDate date = tx.getBookingDateTime().toLocalDate();
      sumByDate.put(date, sumByDate.getOrDefault(date, 0L) + tx.getAmountCents());
    }
    return sumByDate;
  }

  private long balanceAt(LocalDate targetDate, BalanceAnchor anchor, Map<LocalDate, Long> sumByDate) {
    if (targetDate.equals(anchor.date())) {
      return anchor.balanceCents();
    }
    long balance = anchor.balanceCents();
    if (targetDate.isAfter(anchor.date())) {
      LocalDate cursor = anchor.date().plusDays(1);
      while (!cursor.isAfter(targetDate)) {
        balance += sumByDate.getOrDefault(cursor, 0L);
        cursor = cursor.plusDays(1);
      }
      return balance;
    }

    LocalDate cursor = targetDate.plusDays(1);
    while (!cursor.isAfter(anchor.date())) {
      balance -= sumByDate.getOrDefault(cursor, 0L);
      cursor = cursor.plusDays(1);
    }
    return balance;
  }

  private record BalanceAnchor(LocalDate date, long balanceCents, int priority) {}
}
