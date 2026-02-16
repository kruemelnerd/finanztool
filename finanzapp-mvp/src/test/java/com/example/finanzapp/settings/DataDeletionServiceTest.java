package com.example.finanzapp.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.BalanceDailyRepository;
import com.example.finanzapp.repository.CsvArtifactRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataDeletionServiceTest {
  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private CsvArtifactRepository csvArtifactRepository;

  @Mock
  private BalanceDailyRepository balanceDailyRepository;

  @Mock
  private UserRepository userRepository;

  private DataDeletionService service;

  @BeforeEach
  void setUp() {
    service = new DataDeletionService(
        transactionRepository,
        csvArtifactRepository,
        balanceDailyRepository,
        userRepository);
  }

  @Test
  void softDeleteAllDataUpdatesArtifactsAndBalances() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    service.softDeleteAllData(user);

    verify(transactionRepository).softDeleteByUser(eq(user), any(java.time.Instant.class));
    verify(csvArtifactRepository).softDeleteByUser(eq(user), any(java.time.Instant.class));
    verify(balanceDailyRepository).deleteByUser(user);
    verifyNoMoreInteractions(userRepository);
  }

  @Test
  void hardDeleteAccountRemovesAllRecords() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    service.hardDeleteAccount(user);

    verify(transactionRepository).deleteByUser(user);
    verify(csvArtifactRepository).deleteByUser(user);
    verify(balanceDailyRepository).deleteByUser(user);
    verify(userRepository).delete(user);
  }
}
