package de.kruemelnerd.finanzapp.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.kruemelnerd.finanzapp.domain.BalanceDaily;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.BalanceDailyRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BalanceServiceTest {
  @Test
  void computeLast30DaysProducesDailyPoints() {
    BalanceService service = new BalanceService(null);
    LocalDate today = LocalDate.now();

    Transaction yesterday = new Transaction();
    yesterday.setBookingDateTime(LocalDateTime.of(today.minusDays(1), java.time.LocalTime.NOON));
    yesterday.setAmountCents(-2000L);

    Transaction todayTx = new Transaction();
    todayTx.setBookingDateTime(LocalDateTime.of(today, java.time.LocalTime.of(9, 0)));
    todayTx.setAmountCents(5000L);

    List<BalancePoint> points = service.computeLast30Days(10000L, List.of(yesterday, todayTx));

    assertThat(points).hasSize(30);
    BalancePoint lastPoint = points.get(points.size() - 1);
    assertThat(lastPoint.date()).isEqualTo(today);
    assertThat(lastPoint.balanceCents()).isEqualTo(13000L);

    BalancePoint yesterdayPoint = points.get(points.size() - 2);
    assertThat(yesterdayPoint.date()).isEqualTo(today.minusDays(1));
    assertThat(yesterdayPoint.balanceCents()).isEqualTo(8000L);
  }

  @Test
  void materializeLast30DaysPersistsEntries() {
    BalanceDailyRepository repository = mock(BalanceDailyRepository.class);
    BalanceService service = new BalanceService(repository);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    LocalDate start = LocalDate.of(2026, 2, 1);
    LocalDate end = LocalDate.of(2026, 2, 2);
    List<BalancePoint> points = List.of(
        new BalancePoint(start, 100L),
        new BalancePoint(end, 200L));

    service.materializeLast30Days(user, points);

    verify(repository).deleteByUserAndDateBetween(user, start, end);
    ArgumentCaptor<List<BalanceDaily>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());
    List<BalanceDaily> saved = captor.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).getDate()).isEqualTo(start);
    assertThat(saved.get(0).getBalanceCentsEndOfDay()).isEqualTo(100L);
    assertThat(saved.get(1).getDate()).isEqualTo(end);
    assertThat(saved.get(1).getBalanceCentsEndOfDay()).isEqualTo(200L);
  }

  @Test
  void materializeLast30DaysSkipsEmptyPoints() {
    BalanceDailyRepository repository = mock(BalanceDailyRepository.class);
    BalanceService service = new BalanceService(repository);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    service.materializeLast30Days(user, List.of());

    verifyNoInteractions(repository);
  }
}
