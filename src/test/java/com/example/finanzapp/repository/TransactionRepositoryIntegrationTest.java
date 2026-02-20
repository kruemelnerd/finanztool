package com.example.finanzapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionRepositoryIntegrationTest extends RepositoryIntegrationTestBase {
  @Test
  void findByUserAndDeletedAtIsNullOrdersByBookingDateTimeDesc() {
    User user = saveUser("user@example.com");
    User other = saveUser("other@example.com");

    LocalDateTime first = LocalDateTime.of(2026, 2, 1, 10, 0);
    LocalDateTime second = LocalDateTime.of(2026, 2, 2, 9, 0);
    LocalDateTime third = LocalDateTime.of(2026, 2, 3, 8, 0);

    saveTransaction(user, first, 1000L);
    saveDeletedTransaction(user, second, 2000L);
    saveTransaction(user, third, 3000L);
    saveTransaction(other, third, 999L);

    List<Transaction> result = transactionRepository
        .findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getBookingDateTime()).isEqualTo(third);
    assertThat(result.get(1).getBookingDateTime()).isEqualTo(first);
  }

  @Test
  void findActiveUpToFiltersDeletedAndCutoff() {
    User user = saveUser("user@example.com");

    LocalDateTime early = LocalDateTime.of(2026, 2, 1, 10, 0);
    LocalDateTime middle = LocalDateTime.of(2026, 2, 2, 10, 0);
    LocalDateTime late = LocalDateTime.of(2026, 2, 3, 10, 0);

    saveTransaction(user, early, 100L);
    saveDeletedTransaction(user, middle, 200L);
    saveTransaction(user, late, 300L);

    List<Transaction> result = transactionRepository.findActiveUpTo(user, middle.plusHours(1));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getBookingDateTime()).isEqualTo(early);
  }
}
