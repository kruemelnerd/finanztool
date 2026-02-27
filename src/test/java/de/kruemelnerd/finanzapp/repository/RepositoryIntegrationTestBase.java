package de.kruemelnerd.finanzapp.repository;

import de.kruemelnerd.finanzapp.domain.BalanceDaily;
import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
abstract class RepositoryIntegrationTestBase {
  @Autowired
  protected UserRepository userRepository;

  @Autowired
  protected TransactionRepository transactionRepository;

  @Autowired
  protected CsvArtifactRepository csvArtifactRepository;

  @Autowired
  protected BalanceDailyRepository balanceDailyRepository;

  @BeforeEach
  void cleanDatabase() {
    balanceDailyRepository.deleteAll();
    csvArtifactRepository.deleteAllInBatch();
    transactionRepository.deleteAll();
    userRepository.deleteAll();
  }

  @AfterEach
  void cleanDatabaseAfter() {
    balanceDailyRepository.deleteAll();
    csvArtifactRepository.deleteAllInBatch();
    transactionRepository.deleteAll();
    userRepository.deleteAll();
  }

  protected User saveUser(String email) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hashed");
    return userRepository.save(user);
  }

  protected Transaction saveTransaction(User user, LocalDateTime bookingDateTime, long amountCents) {
    Transaction tx = new Transaction();
    tx.setUser(user);
    tx.setBookingDateTime(bookingDateTime);
    tx.setPartnerName("Partner");
    tx.setPurposeText("Purpose");
    tx.setAmountCents(amountCents);
    return transactionRepository.save(tx);
  }

  protected Transaction saveDeletedTransaction(User user, LocalDateTime bookingDateTime, long amountCents) {
    Transaction tx = saveTransaction(user, bookingDateTime, amountCents);
    tx.setDeletedAt(Instant.now());
    return transactionRepository.save(tx);
  }

  protected CsvArtifact saveCsvArtifact(User user, String fileName, boolean deleted) {
    CsvArtifact artifact = new CsvArtifact();
    artifact.setUser(user);
    artifact.setOriginalFileName(fileName);
    artifact.setContentType("text/csv");
    artifact.setBytes(new byte[] {1, 2, 3});
    artifact.setSizeBytes(3L);
    if (deleted) {
      artifact.setDeletedAt(Instant.now());
    }
    return csvArtifactRepository.save(artifact);
  }

  protected BalanceDaily saveBalanceDaily(User user, LocalDate date, long cents) {
    BalanceDaily balance = new BalanceDaily();
    balance.setUser(user);
    balance.setDate(date);
    balance.setBalanceCentsEndOfDay(cents);
    return balanceDailyRepository.save(balance);
  }
}
