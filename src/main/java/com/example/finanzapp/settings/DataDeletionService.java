package com.example.finanzapp.settings;

import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.BalanceDailyRepository;
import com.example.finanzapp.repository.CsvArtifactRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataDeletionService {
  private final TransactionRepository transactionRepository;
  private final CsvArtifactRepository csvArtifactRepository;
  private final BalanceDailyRepository balanceDailyRepository;
  private final UserRepository userRepository;

  public DataDeletionService(
      TransactionRepository transactionRepository,
      CsvArtifactRepository csvArtifactRepository,
      BalanceDailyRepository balanceDailyRepository,
      UserRepository userRepository) {
    this.transactionRepository = transactionRepository;
    this.csvArtifactRepository = csvArtifactRepository;
    this.balanceDailyRepository = balanceDailyRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public void softDeleteAllData(User user) {
    Instant now = Instant.now();
    transactionRepository.softDeleteByUser(user, now);
    csvArtifactRepository.softDeleteByUser(user, now);
    balanceDailyRepository.deleteByUser(user);
  }

  @Transactional
  public void hardDeleteAccount(User user) {
    transactionRepository.deleteByUser(user);
    csvArtifactRepository.deleteByUser(user);
    balanceDailyRepository.deleteByUser(user);
    userRepository.delete(user);
  }
}
