package de.kruemelnerd.finanzapp.importcsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.kruemelnerd.finanzapp.balance.BalanceService;
import de.kruemelnerd.finanzapp.balance.BalancePoint;
import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.rules.CategoryAssignmentService;
import de.kruemelnerd.finanzapp.repository.CsvArtifactRepository;
import de.kruemelnerd.finanzapp.repository.TransactionRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {
  @Mock
  private CsvArtifactRepository csvArtifactRepository;

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private BalanceService balanceService;

  @Mock
  private CategoryAssignmentService categoryAssignmentService;

  private CsvImportService csvImportService;

  @BeforeEach
  void setUp() {
    csvImportService = new CsvImportService(
        csvArtifactRepository,
        transactionRepository,
        balanceService,
        categoryAssignmentService);
  }

  @Test
  void importRejectsEmptyFile() {
    assertThatThrownBy(() -> csvImportService.importCsv(null, "file.csv", "text/csv", new byte[0]))
        .isInstanceOf(CsvImportException.class)
        .hasMessage("CSV file is empty");
  }

  @Test
  void importRejectsOversizedFile() {
    byte[] tooLarge = new byte[(int) CsvImportService.MAX_SIZE_BYTES + 1];
    assertThatThrownBy(() -> csvImportService.importCsv(null, "file.csv", "text/csv", tooLarge))
        .isInstanceOf(CsvImportException.class)
        .hasMessage("CSV exceeds 10MB limit");
  }

  @Test
  void importStoresArtifactTransactionsAndBalance() {
    String csv = String.join("\n",
        "Alter Kontostand;10,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;SONSTIGES;Buchungstext: Test;1,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("EN");

    List<BalancePoint> points = List.of(new BalancePoint(LocalDate.now(), 1000L));
    when(balanceService.computeLast30Days(eq(1000L), anyList())).thenReturn(points);
    when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of());

    CsvImportResult result = csvImportService.importCsv(user, null, "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(1);
    assertThat(result.duplicateCount()).isZero();

    ArgumentCaptor<CsvArtifact> artifactCaptor = ArgumentCaptor.forClass(CsvArtifact.class);
    verify(csvArtifactRepository).save(artifactCaptor.capture());
    CsvArtifact artifact = artifactCaptor.getValue();
    assertThat(artifact.getUser()).isEqualTo(user);
    assertThat(artifact.getOriginalFileName()).isEqualTo("import.csv");
    assertThat(artifact.getBytes()).isEqualTo(bytes);
    assertThat(artifact.getSizeBytes()).isEqualTo(bytes.length);

    ArgumentCaptor<List<Transaction>> txCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionRepository).saveAll(txCaptor.capture());
    List<Transaction> saved = txCaptor.getValue();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getUser()).isEqualTo(user);
    assertThat(saved.get(0).getPayerName()).isNull();
    assertThat(saved.get(0).getBookingText()).isEqualTo("Test");
    assertThat(saved.get(0).getCardNumber()).isNull();
    assertThat(saved.get(0).getCardPaymentText()).isNull();
    assertThat(saved.get(0).getReferenceText()).isNull();

    verify(balanceService).computeLast30Days(eq(1000L), anyList());
    verify(balanceService).materializeLast30Days(user, points);
    verify(categoryAssignmentService).assignForImport(eq(user), anyList());
  }

  @Test
  void importSkipsBalanceWhenStartBalanceMissing() {
    String csv = String.join("\n",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;SONSTIGES;Buchungstext: Test;1,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of());

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(1);
    verify(balanceService, never()).computeLast30Days(anyLong(), anyList());
    verifyNoInteractions(balanceService);
  }

  @Test
  void importSkipsDuplicateTransactions() {
    String csv = String.join("\n",
        "Alter Kontostand;10,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;SONSTIGES;Buchungstext: Test;1,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2026, 2, 1, 0, 0));
    existing.setTransactionType("SONSTIGES");
    existing.setPartnerName("SONSTIGES");
    existing.setPurposeText("Test");
    existing.setAmountCents(100L);

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));
    when(balanceService.computeLast30Days(eq(1000L), anyList())).thenReturn(List.of());

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(0);
    assertThat(result.duplicateCount()).isEqualTo(1);
    verify(categoryAssignmentService, never()).assignForImport(eq(user), anyList());
  }

  @Test
  void importReturnsAllDuplicateSamples() {
    String csv = String.join("\n",
        "Alter Kontostand;10,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: Abo;-31,00",
        "02.02.2026;02.02.2026;UEBERWEISUNG;Buchungstext: Service;-120,00",
        "03.02.2026;03.02.2026;KARTE;Buchungstext: Lunch;-75,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("EN");

    Transaction first = new Transaction();
    first.setUser(user);
    first.setBookingDateTime(java.time.LocalDateTime.of(2026, 2, 1, 0, 0));
    first.setTransactionType("LASTSCHRIFT");
    first.setPartnerName("LASTSCHRIFT");
    first.setPurposeText("Abo");
    first.setAmountCents(-3100L);

    Transaction second = new Transaction();
    second.setUser(user);
    second.setBookingDateTime(java.time.LocalDateTime.of(2026, 2, 2, 0, 0));
    second.setTransactionType("UEBERWEISUNG");
    second.setPartnerName("UEBERWEISUNG");
    second.setPurposeText("Service");
    second.setAmountCents(-12000L);

    Transaction third = new Transaction();
    third.setUser(user);
    third.setBookingDateTime(java.time.LocalDateTime.of(2026, 2, 3, 0, 0));
    third.setTransactionType("KARTE");
    third.setPartnerName("KARTE");
    third.setPurposeText("Lunch");
    third.setAmountCents(-7500L);

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(first, second, third));
    when(balanceService.computeLast30Days(eq(1000L), anyList())).thenReturn(List.of());

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(0);
    assertThat(result.duplicateCount()).isEqualTo(3);
    assertThat(result.duplicateSamples()).hasSize(3);
    assertThat(result.duplicateSamples())
        .containsExactly(
            "2026-02-01 - LASTSCHRIFT - -31.00 EUR",
            "2026-02-02 - UEBERWEISUNG - -120.00 EUR",
            "2026-02-03 - KARTE - -75.00 EUR");
  }

  @Test
  void importReturnsGermanFormattedDuplicateSamplesWhenUserLanguageIsDe() {
    String csv = String.join("\n",
        "Alter Kontostand;10,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.02.2026;01.02.2026;LASTSCHRIFT;Buchungstext: Abo;-31,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("DE");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2026, 2, 1, 0, 0));
    existing.setTransactionType("LASTSCHRIFT");
    existing.setPartnerName("LASTSCHRIFT");
    existing.setPurposeText("Abo");
    existing.setAmountCents(-3100L);

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));
    when(balanceService.computeLast30Days(eq(1000L), anyList())).thenReturn(List.of());

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(0);
    assertThat(result.duplicateCount()).isEqualTo(1);
    assertThat(result.duplicateSamples()).containsExactly("01.02.2026 - LASTSCHRIFT - -31,00 EUR");
  }

  @Test
  void importDetectsDuplicateWhenExistingPurposeContainsCardSuffix() {
    String csv = String.join("\n",
        "Alter Kontostand;10,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "06.02.2026;05.02.2026;Lastschrift / Belastung;Auftraggeber: H&M Buchungstext: H&M, Berlin DE Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung;-28,28");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2026, 2, 6, 0, 0));
    existing.setTransactionType("Lastschrift / Belastung");
    existing.setPartnerName("H&M");
    existing.setPurposeText(
        "H&M, Berlin DE Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung comdirect Visa-Debitkarte");
    existing.setAmountCents(-2828L);

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));
    when(balanceService.computeLast30Days(eq(1000L), anyList())).thenReturn(List.of());

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(0);
    assertThat(result.duplicateCount()).isEqualTo(1);
  }

  @Test
  void importDoesNotTreatDifferentReferenceAsDuplicate() {
    String csv = String.join("\n",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "23.10.2025;23.10.2025;Kartenverfügung;Buchungstext: REST. DANA SNACK-IT, AMSTERDAM NL Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung comdirect Visa-Debitkarte 2025-10-20 00:00:00 Ref. 6P2C21SF0YDH26QB/60016;-3,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2025, 10, 23, 0, 0));
    existing.setTransactionType("Kartenverfügung");
    existing.setPartnerName("REST. DANA SNACK-IT");
    existing.setPurposeText("REST. DANA SNACK-IT, AMSTERDAM NL");
    existing.setAmountCents(-300L);
    existing.setReferenceText("6P2C21SF0YDH26QB/83955");

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));
    when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(1);
    assertThat(result.duplicateCount()).isZero();
  }

  @Test
  void importTreatsSameReferenceAsDuplicate() {
    String csv = String.join("\n",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "23.10.2025;23.10.2025;Kartenverfügung;Buchungstext: REST. DANA SNACK-IT, AMSTERDAM NL Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung comdirect Visa-Debitkarte 2025-10-20 00:00:00 Ref. 6P2C21SF0YDH26QB/83955;-3,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2025, 10, 23, 0, 0));
    existing.setTransactionType("Andere Beschreibung");
    existing.setPartnerName("Irgendein Name");
    existing.setPurposeText("Abweichender Text");
    existing.setAmountCents(-300L);
    existing.setReferenceText("6P2C21SF0YDH26QB/83955");

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(0);
    assertThat(result.duplicateCount()).isEqualTo(1);
  }

  @Test
  void importTreatsReferenceCaseAndDelimiterVariantsAsDuplicate() {
    String csv = String.join("\n",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "23.10.2025;23.10.2025;Kartenverfügung;Buchungstext: REST. DANA SNACK-IT, AMSTERDAM NL REF: 6P2C21SF0YDH26QB/83955;-3,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2025, 10, 23, 0, 0));
    existing.setTransactionType("Andere Beschreibung");
    existing.setPartnerName("Irgendein Name");
    existing.setPurposeText("Abweichender Text");
    existing.setAmountCents(-300L);
    existing.setReferenceText("6P2C21SF0YDH26QB/83955");

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(0);
    assertThat(result.duplicateCount()).isEqualTo(1);
  }

  @Test
  void importTreatsHashReferenceDelimiterAsDuplicate() {
    String csv = String.join("\n",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "23.10.2025;23.10.2025;Kartenverfügung;Buchungstext: REST. DANA SNACK-IT, AMSTERDAM NL Ref# 6P2C21SF0YDH26QB/83955;-3,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2025, 10, 23, 0, 0));
    existing.setTransactionType("Andere Beschreibung");
    existing.setPartnerName("Irgendein Name");
    existing.setPurposeText("Abweichender Text");
    existing.setAmountCents(-300L);
    existing.setReferenceText("6P2C21SF0YDH26QB/83955");

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(0);
    assertThat(result.duplicateCount()).isEqualTo(1);
  }

  @Test
  void importIgnoresMalformedReferenceTokenAndFallsBackToCompositeKey() {
    String csv = String.join("\n",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "23.10.2025;23.10.2025;Karte;Buchungstext: Coffee Ref.:;-3,00");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    Transaction existing = new Transaction();
    existing.setUser(user);
    existing.setBookingDateTime(java.time.LocalDateTime.of(2025, 10, 24, 0, 0));
    existing.setTransactionType("Karte");
    existing.setPartnerName("Cafe");
    existing.setPurposeText("Coffee");
    existing.setAmountCents(-900L);
    existing.setReferenceText(":");

    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of(existing));
    when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(1);
    assertThat(result.duplicateCount()).isZero();
  }

  @Test
  void importPersistsSplitBookingComponents() {
    String csv = String.join("\n",
        "Alter Kontostand;10,00 EUR",
        "Buchungstag;Wertstellung (Valuta);Vorgang;Buchungstext;Umsatz in EUR",
        "01.09.2025;01.09.2025;Lastschrift / Belastung;Auftraggeber: PayPal Europe S.a.r.l. et Cie S.C.A Buchungstext: PayPal Europe S.a.r.l. et Cie S.C.A, Luxembourg DE Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung comdirect Visa-Debitkarte 2025-09-01 00:00:00 Ref. OWGZRIXA11DPG8SB/32663;-12,34");
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(user))
        .thenReturn(List.of());

    CsvImportResult result = csvImportService.importCsv(user, "file.csv", "text/csv", bytes);

    assertThat(result.importedCount()).isEqualTo(1);

    ArgumentCaptor<List<Transaction>> txCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionRepository).saveAll(txCaptor.capture());
    Transaction saved = txCaptor.getValue().get(0);
    assertThat(saved.getPayerName()).isEqualTo("PayPal Europe S.a.r.l. et Cie S.C.A");
    assertThat(saved.getBookingText()).isEqualTo("PayPal Europe S.a.r.l. et Cie S.C.A, Luxembourg DE");
    assertThat(saved.getCardNumber()).isEqualTo("4871 78XX XXXX 8491");
    assertThat(saved.getCardPaymentText()).isEqualTo("comdirect Visa-Debitkarte 2025-09-01 00:00:00");
    assertThat(saved.getReferenceText()).isEqualTo("OWGZRIXA11DPG8SB/32663");
  }
}
