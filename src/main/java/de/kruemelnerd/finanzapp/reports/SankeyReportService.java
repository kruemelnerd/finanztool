package de.kruemelnerd.finanzapp.reports;

import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;
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
      return new SankeyReportData(year, List.of(centerNode()), List.of());
    }

    List<Transaction> transactions = transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user.get());

    Map<Integer, Long> incomeByCategory = new LinkedHashMap<>();
    Map<Integer, Long> expenseByCategory = new LinkedHashMap<>();
    Map<Integer, String> incomeCategoryLabels = new LinkedHashMap<>();
    Map<Integer, String> expenseCategoryLabels = new LinkedHashMap<>();

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

      Integer categoryId = transaction.getCategory().getId();
      String categoryLabel = transaction.getCategory().getParent().getName() + " -> " + transaction.getCategory().getName();

      if (transaction.getAmountCents() > 0) {
        incomeCategoryLabels.put(categoryId, categoryLabel);
        incomeByCategory.put(categoryId, incomeByCategory.getOrDefault(categoryId, 0L) + amountCents);
        continue;
      }

      if (transaction.getAmountCents() < 0) {
        expenseCategoryLabels.put(categoryId, categoryLabel);
        expenseByCategory.put(categoryId, expenseByCategory.getOrDefault(categoryId, 0L) + amountCents);
      }
    }

    List<SankeyNode> nodes = new ArrayList<>();
    nodes.add(centerNode());

    incomeCategoryLabels.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .forEach(entry -> nodes.add(new SankeyNode(incomeCategoryNodeId(entry.getKey()), entry.getValue())));

    expenseCategoryLabels.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .forEach(entry -> nodes.add(new SankeyNode(expenseCategoryNodeId(entry.getKey()), entry.getValue())));

    List<SankeyLink> links = new ArrayList<>();
    incomeByCategory.entrySet().stream()
        .sorted(Comparator.comparing(entry -> incomeCategoryLabels.get(entry.getKey())))
        .forEach(entry -> links.add(new SankeyLink(
            incomeCategoryNodeId(entry.getKey()),
            "center",
            entry.getValue())));

    expenseByCategory.entrySet().stream()
        .sorted(Comparator.comparing(entry -> expenseCategoryLabels.get(entry.getKey())))
        .forEach(entry -> links.add(new SankeyLink(
            "center",
            expenseCategoryNodeId(entry.getKey()),
            entry.getValue())));

    return new SankeyReportData(year, List.copyOf(nodes), links);
  }

  public int defaultYear() {
    return LocalDate.now().getYear() - 1;
  }

  private SankeyNode centerNode() {
    return new SankeyNode("center", "Center");
  }

  private String incomeCategoryNodeId(Integer categoryId) {
    return "income-cat-" + categoryId;
  }

  private String expenseCategoryNodeId(Integer categoryId) {
    return "expense-cat-" + categoryId;
  }

  private Optional<User> resolveUser(UserDetails userDetails) {
    if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(userDetails.getUsername());
  }

  public record SankeyReportData(int year, List<SankeyNode> nodes, List<SankeyLink> links) {}

  public record SankeyNode(String id, String label) {}

  public record SankeyLink(String source, String target, long valueCents) {}
}
