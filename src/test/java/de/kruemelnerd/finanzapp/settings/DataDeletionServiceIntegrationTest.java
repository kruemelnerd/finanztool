package de.kruemelnerd.finanzapp.settings;

import static org.assertj.core.api.Assertions.assertThat;

import de.kruemelnerd.finanzapp.domain.BalanceDaily;
import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.BalanceDailyRepository;
import de.kruemelnerd.finanzapp.repository.CsvArtifactRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import de.kruemelnerd.finanzapp.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DataDeletionServiceIntegrationTest {
  @Autowired
  private DataDeletionService dataDeletionService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private CsvArtifactRepository csvArtifactRepository;

  @Autowired
  private BalanceDailyRepository balanceDailyRepository;

  @BeforeEach
  void cleanDatabase() {
    balanceDailyRepository.deleteAll();
    csvArtifactRepository.deleteAllInBatch();
    transactionRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void softDeleteAllDataMarksRecords() {
    User user = createUser();
    Transaction tx = createTransaction(user, 1000L);
    CsvArtifact artifact = createArtifact(user);
    createBalance(user);

    dataDeletionService.softDeleteAllData(user);

    Transaction updatedTx = transactionRepository.findById(tx.getId()).orElseThrow();
    CsvArtifact updatedArtifact = csvArtifactRepository.findById(artifact.getId()).orElseThrow();

    assertThat(updatedTx.getDeletedAt()).isNotNull();
    assertThat(updatedArtifact.getDeletedAt()).isNotNull();
    assertThat(balanceDailyRepository.findByUserAndDateBetween(
        user, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))).isEmpty();
    assertThat(userRepository.findByEmail(user.getEmail())).isPresent();
  }

  @Test
  void hardDeleteAccountRemovesUserAndData() {
    User user = createUser();
    createTransaction(user, 1000L);
    createArtifact(user);
    createBalance(user);

    dataDeletionService.hardDeleteAccount(user);

    assertThat(userRepository.findByEmail(user.getEmail())).isEmpty();
    assertThat(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .isEmpty();
    assertThat(csvArtifactRepository.findByUserAndDeletedAtIsNull(user)).isEmpty();
    assertThat(balanceDailyRepository.findAll()).isEmpty();
  }

  private User createUser() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    return userRepository.save(user);
  }

  private Transaction createTransaction(User user, long amount) {
    Transaction tx = new Transaction();
    tx.setUser(user);
    tx.setBookingDateTime(LocalDateTime.of(2026, 2, 1, 0, 0));
    tx.setPartnerName("Partner");
    tx.setPurposeText("Purpose");
    tx.setAmountCents(amount);
    return transactionRepository.save(tx);
  }

  private CsvArtifact createArtifact(User user) {
    CsvArtifact artifact = new CsvArtifact();
    artifact.setUser(user);
    artifact.setOriginalFileName("import.csv");
    artifact.setContentType("text/csv");
    artifact.setBytes(new byte[] {1, 2, 3});
    artifact.setSizeBytes(3L);
    artifact.setDeletedAt(null);
    return csvArtifactRepository.save(artifact);
  }

  private BalanceDaily createBalance(User user) {
    BalanceDaily balanceDaily = new BalanceDaily();
    balanceDaily.setUser(user);
    balanceDaily.setDate(LocalDate.now());
    balanceDaily.setBalanceCentsEndOfDay(1000L);
    return balanceDailyRepository.save(balanceDaily);
  }
}
