package com.example.finanzapp.repository;

import com.example.finanzapp.domain.BalanceDaily;
import com.example.finanzapp.domain.User;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface BalanceDailyRepository extends JpaRepository<BalanceDaily, Integer> {
  List<BalanceDaily> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);

  long countByUser(User user);

  @Transactional
  void deleteByUserAndDateBetween(User user, LocalDate start, LocalDate end);

  @Transactional
  void deleteByUser(User user);
}
