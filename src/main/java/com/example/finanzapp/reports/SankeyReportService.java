package com.example.finanzapp.reports;

import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SankeyReportService {
  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;

  public SankeyReportService(
      UserRepository userRepository,
      TransactionRepository transactionRepository) {
    this.userRepository = userRepository;
    this.transactionRepository = transactionRepository;
  }

  @Transactional(readOnly = true)
  public List<Integer> loadAvailableYears(UserDetails userDetails) {
    Optional<User> user = resolveUser(userDetails);
    int fallbackYear = defaultYear();
    if (user.isEmpty()) {
      return List.of(fallbackYear);
    }

    List<Transaction> transactions = transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user.get());
    Set<Integer> years = new TreeSet<>(Comparator.reverseOrder());
    years.add(fallbackYear);
    for (Transaction transaction : transactions) {
      if (transaction.getBookingDateTime() != null) {
        years.add(transaction.getBookingDateTime().getYear());
      }
    }
    return List.copyOf(years);
  }

  @Transactional(readOnly = true)
  public SankeyReportData buildReport(UserDetails userDetails, int year) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      return new SankeyReportData(year, List.of(baseIncomeNode(), baseExpenseNode()), List.of());
    }

    List<Transaction> transactions = transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user.get());

    Map<LinkKey, Long> sumsByLink = new LinkedHashMap<>();
    Map<Integer, String> categoryLabels = new LinkedHashMap<>();

    for (Transaction transaction : transactions) {
      if (transaction.getBookingDateTime() == null || transaction.getBookingDateTime().getYear() != year) {
        continue;
      }
      if (transaction.getCategory() == null || transaction.getCategory().getId() == null) {
        continue;
      }
      if (transaction.getCategory().getParent() == null) {
        continue;
      }

      long amountCents = Math.abs(transaction.getAmountCents());
      if (amountCents == 0L) {
        continue;
      }

      String source = transaction.getAmountCents() >= 0 ? "income" : "expense";
      Integer categoryId = transaction.getCategory().getId();
      String categoryLabel = transaction.getCategory().getParent().getName() + " -> " + transaction.getCategory().getName();

      categoryLabels.put(categoryId, categoryLabel);
      LinkKey key = new LinkKey(source, categoryId);
      sumsByLink.put(key, sumsByLink.getOrDefault(key, 0L) + amountCents);
    }

    List<SankeyNode> nodes = new ArrayList<>();
    nodes.add(baseIncomeNode());
    nodes.add(baseExpenseNode());

    categoryLabels.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .forEach(entry -> nodes.add(new SankeyNode(categoryNodeId(entry.getKey()), entry.getValue())));

    List<SankeyLink> links = sumsByLink.entrySet().stream()
        .sorted(Comparator
            .comparing((Map.Entry<LinkKey, Long> entry) -> entry.getKey().source())
            .thenComparing(entry -> categoryLabels.get(entry.getKey().categoryId())))
        .map(entry -> new SankeyLink(
            entry.getKey().source(),
            categoryNodeId(entry.getKey().categoryId()),
            entry.getValue()))
        .toList();

    return new SankeyReportData(year, List.copyOf(nodes), links);
  }

  public int defaultYear() {
    return LocalDate.now().getYear() - 1;
  }

  private SankeyNode baseIncomeNode() {
    return new SankeyNode("income", "Income");
  }

  private SankeyNode baseExpenseNode() {
    return new SankeyNode("expense", "Expense");
  }

  private String categoryNodeId(Integer categoryId) {
    return "cat-" + categoryId;
  }

  private Optional<User> resolveUser(UserDetails userDetails) {
    if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(userDetails.getUsername());
  }

  private record LinkKey(String source, Integer categoryId) {}

  public record SankeyReportData(int year, List<SankeyNode> nodes, List<SankeyLink> links) {}

  public record SankeyNode(String id, String label) {}

  public record SankeyLink(String source, String target, long valueCents) {}
}
