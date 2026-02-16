package com.example.finanzapp.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.finanzapp.domain.CsvArtifact;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.importcsv.CsvParser;
import com.example.finanzapp.repository.CsvArtifactRepository;
import com.example.finanzapp.repository.TransactionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountBalanceServiceTest {
  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private CsvArtifactRepository csvArtifactRepository;

  @Test
  void computesCurrentAndRangeFromCsvAndTransactions() {
    AccountBalanceService service = new AccountBalanceService(transactionRepository, csvArtifactRepository);
    User user = user();

    String csv = String.join("\n",
        "Neuer Kontostand,\"150,00 EUR\"",
        "Alter Kontostand,\"100,00 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "02.01.2026,02.01.2026,SONSTIGES,\"Buchungstext: Zwei\",\"30,00\"",
        "01.01.2026,01.01.2026,SONSTIGES,\"Buchungstext: Eins\",\"20,00\"");

    CsvArtifact artifact = new CsvArtifact();
    artifact.setBytes(csv.getBytes(StandardCharsets.UTF_8));

    Transaction tx1 = tx(user, LocalDateTime.of(2026, 1, 1, 0, 0), 2000L);
    Transaction tx2 = tx(user, LocalDateTime.of(2026, 1, 2, 0, 0), 3000L);

    when(csvArtifactRepository.findByUserAndDeletedAtIsNull(eq(user))).thenReturn(List.of(artifact));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of(tx2, tx1));

    Optional<Long> current = service.computeCurrentBalanceCents(user);
    List<BalancePoint> points = service.computeRange(user, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

    assertThat(current).contains(15000L);
    assertThat(points).containsExactly(
        new BalancePoint(LocalDate.of(2026, 1, 1), 12000L),
        new BalancePoint(LocalDate.of(2026, 1, 2), 15000L),
        new BalancePoint(LocalDate.of(2026, 1, 3), 15000L));
  }

  @Test
  void prefersCurrentBalanceConsistencyWhenOldBalanceIsInconsistent() {
    AccountBalanceService service = new AccountBalanceService(transactionRepository, csvArtifactRepository);
    User user = user();

    String csv = String.join("\n",
        "Neuer Kontostand,\"150,00 EUR\"",
        "Alter Kontostand,\"100,00 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.01.2026,01.01.2026,SONSTIGES,\"Buchungstext: Eins\",\"20,00\"");

    CsvArtifact artifact = new CsvArtifact();
    artifact.setBytes(csv.getBytes(StandardCharsets.UTF_8));

    Transaction tx = tx(user, LocalDateTime.of(2026, 1, 1, 0, 0), 2000L);

    when(csvArtifactRepository.findByUserAndDeletedAtIsNull(eq(user))).thenReturn(List.of(artifact));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of(tx));

    Optional<Long> current = service.computeCurrentBalanceCents(user);
    assertThat(current).contains(15000L);

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of());

    Optional<Long> afterDelete = service.computeCurrentBalanceCents(user);
    assertThat(afterDelete).contains(13000L);
  }

  @Test
  void prefersLatestAnchorAcrossOverlappingImports() {
    AccountBalanceService service = new AccountBalanceService(transactionRepository, csvArtifactRepository);
    User user = user();

    CsvArtifact latest = artifact(String.join("\n",
        "Neuer Kontostand,\"200,00 EUR\"",
        "Alter Kontostand,\"500,00 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.03.2025,01.03.2025,LASTSCHRIFT,\"Buchungstext: A\",\"-100,00\"",
        "02.03.2025,02.03.2025,LASTSCHRIFT,\"Buchungstext: B\",\"-200,00\""));

    CsvArtifact middle = artifact(String.join("\n",
        "Neuer Kontostand,\"100,00 EUR\"",
        "Alter Kontostand,\"700,00 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "05.02.2025,05.02.2025,LASTSCHRIFT,\"Buchungstext: C\",\"-300,00\"",
        "01.03.2025,01.03.2025,LASTSCHRIFT,\"Buchungstext: A\",\"-100,00\"",
        "02.03.2025,02.03.2025,LASTSCHRIFT,\"Buchungstext: B\",\"-200,00\""));

    CsvArtifact oldest = artifact(String.join("\n",
        "Neuer Kontostand,\"100,00 EUR\"",
        "Alter Kontostand,\"1.000,00 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "10.01.2025,10.01.2025,LASTSCHRIFT,\"Buchungstext: D\",\"-600,00\"",
        "05.02.2025,05.02.2025,LASTSCHRIFT,\"Buchungstext: C\",\"-300,00\""));

    List<Transaction> activeTransactions = List.of(
        tx(user, LocalDateTime.of(2025, 3, 2, 12, 0), -20000L),
        tx(user, LocalDateTime.of(2025, 3, 1, 12, 0), -10000L),
        tx(user, LocalDateTime.of(2025, 2, 5, 12, 0), -30000L),
        tx(user, LocalDateTime.of(2025, 1, 10, 12, 0), -60000L));

    when(csvArtifactRepository.findByUserAndDeletedAtIsNull(eq(user)))
        .thenReturn(List.of(latest, middle, oldest));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(activeTransactions);

    Optional<Long> current = service.computeCurrentBalanceCents(user);

    assertThat(current).contains(20000L);
  }

  @Test
  void computesExpectedBalanceForRealOverlapFixtures() {
    AccountBalanceService service = new AccountBalanceService(transactionRepository, csvArtifactRepository);
    User user = user();

    byte[] overlap1 = readFixture("fixtures/umsaetze_mock_overlap_1_20250811_bis_20260206.csv");
    byte[] overlap2 = readFixture("fixtures/umsaetze_mock_overlap_2_20250720_bis_20260115.csv");
    byte[] overlap3 = readFixture("fixtures/umsaetze_mock_overlap_3_20250624_bis_20251220.csv");

    CsvArtifact artifact1 = new CsvArtifact();
    artifact1.setBytes(overlap1);
    CsvArtifact artifact2 = new CsvArtifact();
    artifact2.setBytes(overlap2);
    CsvArtifact artifact3 = new CsvArtifact();
    artifact3.setBytes(overlap3);

    List<Transaction> active = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    CsvParser parser = new CsvParser();
    for (byte[] bytes : List.of(overlap1, overlap2, overlap3)) {
      for (Transaction tx : parser.parse(bytes).transactions()) {
        tx.setUser(user);
        String key = buildKey(tx);
        if (seen.add(key)) {
          active.add(tx);
        }
      }
    }
    active.sort(Comparator.comparing(Transaction::getBookingDateTime).reversed());

    when(csvArtifactRepository.findByUserAndDeletedAtIsNull(eq(user)))
        .thenReturn(List.of(artifact1, artifact2, artifact3));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(active);

    Optional<Long> current = service.computeCurrentBalanceCents(user);

    assertThat(current).contains(-198224L);
  }

  private User user() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    return user;
  }

  private Transaction tx(User user, LocalDateTime dateTime, long cents) {
    Transaction tx = new Transaction();
    tx.setUser(user);
    tx.setBookingDateTime(dateTime);
    tx.setPartnerName("Sample");
    tx.setPurposeText("Sample");
    tx.setAmountCents(cents);
    return tx;
  }

  private CsvArtifact artifact(String csv) {
    CsvArtifact artifact = new CsvArtifact();
    artifact.setBytes(csv.getBytes(StandardCharsets.UTF_8));
    return artifact;
  }

  private byte[] readFixture(String classpathPath) {
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath)) {
      if (in == null) {
        throw new IllegalArgumentException("Fixture not found: " + classpathPath);
      }
      return in.readAllBytes();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private String buildKey(Transaction transaction) {
    String date = transaction.getBookingDateTime().toLocalDate().toString();
    String amount = Long.toString(transaction.getAmountCents());
    String name = normalize(transaction.getPartnerName());
    String purpose = normalizePurpose(transaction.getPurposeText());
    String type = normalize(transaction.getTransactionType());
    return String.join("|", date, amount, name, purpose, type);
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private String normalizePurpose(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim();
    int bookingTextIndex = indexOfIgnoreCase(normalized, "Buchungstext:");
    if (bookingTextIndex >= 0) {
      normalized = normalized.substring(bookingTextIndex + "Buchungstext:".length()).trim();
    }
    int cardIndex = indexOfIgnoreCase(normalized, "Karte Nr.");
    if (cardIndex >= 0) {
      normalized = normalized.substring(0, cardIndex).trim();
    }
    int referenceIndex = indexOfIgnoreCase(normalized, "Ref.");
    if (referenceIndex >= 0) {
      normalized = normalized.substring(0, referenceIndex).trim();
    }
    return normalized.toLowerCase(java.util.Locale.ROOT);
  }

  private int indexOfIgnoreCase(String source, String marker) {
    return source.toLowerCase(java.util.Locale.ROOT).indexOf(marker.toLowerCase(java.util.Locale.ROOT));
  }
}
