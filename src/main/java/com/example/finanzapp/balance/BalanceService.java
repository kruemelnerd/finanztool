package com.example.finanzapp.balance;

import com.example.finanzapp.domain.BalanceDaily;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.BalanceDailyRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BalanceService {
  private final BalanceDailyRepository balanceDailyRepository;

  public BalanceService(BalanceDailyRepository balanceDailyRepository) {
    this.balanceDailyRepository = balanceDailyRepository;
  }

  public List<BalancePoint> computeLast30Days(long startBalanceCents, List<Transaction> transactions) {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusDays(29);

    List<Transaction> sorted = new ArrayList<>(transactions);
    sorted.sort(Comparator.comparing(Transaction::getBookingDateTime));

    Map<LocalDate, Long> sumByDate = new HashMap<>();
    for (Transaction tx : sorted) {
      LocalDate date = tx.getBookingDateTime().toLocalDate();
      sumByDate.put(date, sumByDate.getOrDefault(date, 0L) + tx.getAmountCents());
    }

    List<BalancePoint> points = new ArrayList<>();
    long cumulative = startBalanceCents;
    LocalDate cursor = startDate;
    while (!cursor.isAfter(endDate)) {
      cumulative += sumByDate.getOrDefault(cursor, 0L);
      points.add(new BalancePoint(cursor, cumulative));
      cursor = cursor.plusDays(1);
    }
    return points;
  }

  @Transactional
  public void materializeLast30Days(User user, List<BalancePoint> points) {
    if (points.isEmpty()) {
      return;
    }
    LocalDate start = points.get(0).date();
    LocalDate end = points.get(points.size() - 1).date();
    balanceDailyRepository.deleteByUserAndDateBetween(user, start, end);
    balanceDailyRepository.flush();

    List<BalanceDaily> daily = new ArrayList<>();
    for (BalancePoint point : points) {
      BalanceDaily entry = new BalanceDaily();
      entry.setUser(user);
      entry.setDate(point.date());
      entry.setBalanceCentsEndOfDay(point.balanceCents());
      daily.add(entry);
    }
    balanceDailyRepository.saveAll(daily);
  }

  public List<BalancePoint> loadRange(User user, LocalDate start, LocalDate end) {
    return balanceDailyRepository.findByUserAndDateBetween(user, start, end).stream()
        .sorted(Comparator.comparing(BalanceDaily::getDate))
        .map(entry -> new BalancePoint(entry.getDate(), entry.getBalanceCentsEndOfDay()))
        .collect(Collectors.toList());
  }
}
