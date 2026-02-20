package com.example.finanzapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finanzapp.domain.BalanceDaily;
import com.example.finanzapp.domain.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceDailyRepositoryIntegrationTest extends RepositoryIntegrationTestBase {
  @Test
  void findByUserAndDateBetweenReturnsRange() {
    User user = saveUser("user@example.com");
    LocalDate start = LocalDate.of(2026, 2, 1);
    LocalDate mid = LocalDate.of(2026, 2, 2);
    LocalDate end = LocalDate.of(2026, 2, 3);

    saveBalanceDaily(user, start, 100L);
    saveBalanceDaily(user, mid, 200L);
    saveBalanceDaily(user, end, 300L);

    List<BalanceDaily> result = balanceDailyRepository.findByUserAndDateBetween(user, start, mid);

    assertThat(result)
        .extracting(BalanceDaily::getDate)
        .containsExactlyInAnyOrder(start, mid);
  }

  @Test
  void deleteByUserAndDateBetweenRemovesRange() {
    User user = saveUser("user@example.com");
    User other = saveUser("other@example.com");

    LocalDate start = LocalDate.of(2026, 2, 1);
    LocalDate mid = LocalDate.of(2026, 2, 2);
    LocalDate end = LocalDate.of(2026, 2, 3);

    saveBalanceDaily(user, start, 100L);
    saveBalanceDaily(user, mid, 200L);
    saveBalanceDaily(user, end, 300L);
    saveBalanceDaily(other, mid, 999L);

    balanceDailyRepository.deleteByUserAndDateBetween(user, start, mid);

    List<BalanceDaily> remaining = balanceDailyRepository.findAll();

    assertThat(remaining)
        .extracting(BalanceDaily::getDate)
        .contains(end, mid)
        .doesNotContain(start);
  }
}
