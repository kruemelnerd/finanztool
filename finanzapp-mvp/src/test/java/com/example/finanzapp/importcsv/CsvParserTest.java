package com.example.finanzapp.importcsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.finanzapp.domain.Transaction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {
  private final CsvParser parser = new CsvParser();

  @Test
  void parseExtractsStartBalanceAndTransactions() {
    String csv = String.join("\n",
        "Neuer Kontostand,\"334,78 EUR\"",
        "Alter Kontostand,\"908,13 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.02.2026,01.02.2026,UEBERWEISUNG,\"Auftraggeber: ACME GmbH Buchungstext: Rechnung 42\",\"-52,84\"",
        "02.02.2026,02.02.2026,LASTSCHRIFT,\"Buchungstext: Abo\",\"-31\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.startBalanceCents()).isEqualTo(90813L);
    assertThat(result.currentBalanceCents()).isEqualTo(33478L);
    List<Transaction> transactions = result.transactions();
    assertThat(transactions).hasSize(2);

    Transaction first = transactions.get(0);
    assertThat(first.getPartnerName()).isEqualTo("ACME GmbH");
    assertThat(first.getPurposeText()).isEqualTo("Rechnung 42");
    assertThat(first.getPayerName()).isEqualTo("ACME GmbH");
    assertThat(first.getBookingText()).isEqualTo("Rechnung 42");
    assertThat(first.getCardNumber()).isNull();
    assertThat(first.getCardPaymentText()).isNull();
    assertThat(first.getReferenceText()).isNull();
    assertThat(first.getAmountCents()).isEqualTo(-5284L);

    Transaction second = transactions.get(1);
    assertThat(second.getPartnerName()).isEqualTo("LASTSCHRIFT");
    assertThat(second.getPurposeText()).isEqualTo("Abo");
    assertThat(second.getPayerName()).isNull();
    assertThat(second.getBookingText()).isEqualTo("Abo");
    assertThat(second.getAmountCents()).isEqualTo(-3100L);
  }

  @Test
  void parseFailsWhenHeaderMissing() {
    String csv = String.join("\n",
        "Alter Kontostand,\"908,13 EUR\"",
        "Foo,Bar");

    assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(CsvImportException.class)
        .hasMessage("CSV header not found");
  }

  @Test
  void parseHandlesThousandSeparatorsAndWholeEuro() {
    String csv = String.join("\n",
        "Alter Kontostand,\"1.234,56 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "03.02.2026,03.02.2026,SONSTIGES,\"Buchungstext: Test\",\"422\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.startBalanceCents()).isEqualTo(123456L);
    assertThat(result.currentBalanceCents()).isEqualTo(165656L);
    assertThat(result.transactions()).hasSize(1);
    assertThat(result.transactions().get(0).getAmountCents()).isEqualTo(42200L);
  }

  @Test
  void parseReturnsNullStartBalanceWhenMissing() {
    String csv = String.join("\n",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "04.02.2026,04.02.2026,SONSTIGES,\"Buchungstext: Test\",\"1,00\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.startBalanceCents()).isNull();
    assertThat(result.currentBalanceCents()).isNull();
  }

  @Test
  void parseSkipsMetaLinesAfterDataAndFindsStartBalance() {
    String csv = String.join("\n",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.02.2026,01.02.2026,SONSTIGES,\"Buchungstext: Test\",\"-10,00\"",
        ",,,,",
        "Alter Kontostand,\"908,13 EUR\",,,",
        "");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.startBalanceCents()).isEqualTo(90813L);
    assertThat(result.currentBalanceCents()).isEqualTo(89813L);
    assertThat(result.transactions()).hasSize(1);
  }

  @Test
  void parseHandlesAmountsWithEmbeddedQuotes() {
    String csv = String.join("\n",
        "Alter Kontostand,\"908,13 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.02.2026,01.02.2026,UEBERWEISUNG,\"Buchungstext: Test\",\"\"\"-52,84\"\"\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.startBalanceCents()).isEqualTo(90813L);
    assertThat(result.currentBalanceCents()).isEqualTo(85529L);
    assertThat(result.transactions()).hasSize(1);
    assertThat(result.transactions().get(0).getAmountCents()).isEqualTo(-5284L);
  }

  @Test
  void parseDerivesStartBalanceFromNewBalanceWhenOldMissing() {
    String csv = String.join("\n",
        "Neuer Kontostand,\"334,78 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.02.2026,01.02.2026,UEBERWEISUNG,\"Buchungstext: Test\",\"-34,78\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.startBalanceCents()).isEqualTo(36956L);
    assertThat(result.currentBalanceCents()).isEqualTo(33478L);
  }

  @Test
  void parseRejectsInvalidBookingDateWhenRowHasData() {
    String csv = String.join("\n",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "invalid,01.02.2026,SONSTIGES,\"Buchungstext: Test\",\"-10,00\"");

    assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(CsvImportException.class)
        .hasMessageContaining("Invalid Buchungstag");
  }

  @Test
  void parseExtractsPurposeOnlyBetweenBuchungstextAndKarteNr() {
    String csv = String.join("\n",
        "Alter Kontostand,\"908,13 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "06.02.2026,05.02.2026,Lastschrift / Belastung,\"Auftraggeber: H&M Buchungstext: H&M, Berlin DE Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung comdirect Visa-Debitkarte 2026-02-06 00:00:00 Ref. 0H3JUJD0IB6IIDLR/89286\",\"-28,28\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.transactions()).hasSize(1);
    Transaction transaction = result.transactions().get(0);
    assertThat(transaction.getPurposeText()).isEqualTo("H&M, Berlin DE");
    assertThat(transaction.getPartnerName()).isEqualTo("H&M");
    assertThat(transaction.getPayerName()).isEqualTo("H&M");
    assertThat(transaction.getBookingText()).isEqualTo("H&M, Berlin DE");
    assertThat(transaction.getCardNumber()).isEqualTo("4871 78XX XXXX 8491");
    assertThat(transaction.getCardPaymentText()).isEqualTo("comdirect Visa-Debitkarte 2026-02-06 00:00:00");
    assertThat(transaction.getReferenceText()).isEqualTo("0H3JUJD0IB6IIDLR/89286");
  }

  @Test
  void parseExtractsAllBookingTextComponentsIntoDedicatedFields() {
    String csv = String.join("\n",
        "Alter Kontostand,\"908,13 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.09.2025,01.09.2025,Lastschrift / Belastung,\"Auftraggeber: PayPal Europe S.a.r.l. et Cie S.C.A Buchungstext: PayPal Europe S.a.r.l. et Cie S.C.A, Luxembourg DE Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung comdirect Visa-Debitkarte 2025-09-01 00:00:00 Ref. OWGZRIXA11DPG8SB/32663\",\"-12,34\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.transactions()).hasSize(1);
    Transaction transaction = result.transactions().get(0);
    assertThat(transaction.getPayerName()).isEqualTo("PayPal Europe S.a.r.l. et Cie S.C.A");
    assertThat(transaction.getBookingText()).isEqualTo("PayPal Europe S.a.r.l. et Cie S.C.A, Luxembourg DE");
    assertThat(transaction.getCardNumber()).isEqualTo("4871 78XX XXXX 8491");
    assertThat(transaction.getCardPaymentText()).isEqualTo("comdirect Visa-Debitkarte 2025-09-01 00:00:00");
    assertThat(transaction.getReferenceText()).isEqualTo("OWGZRIXA11DPG8SB/32663");
  }

  @Test
  void parseExtractsReferenceWithoutCardSection() {
    String csv = String.join("\n",
        "Alter Kontostand,\"908,13 EUR\"",
        "Buchungstag,Wertstellung (Valuta),Vorgang,Buchungstext,Umsatz in EUR",
        "01.09.2025,01.09.2025,KARTE,\"Buchungstext: Space Marine 2 Ref. DL5C28T842OQG7NU\",\"-12,34\"");

    CsvParsingResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(result.transactions()).hasSize(1);
    Transaction transaction = result.transactions().get(0);
    assertThat(transaction.getPurposeText()).isEqualTo("Space Marine 2");
    assertThat(transaction.getBookingText()).isEqualTo("Space Marine 2");
    assertThat(transaction.getReferenceText()).isEqualTo("DL5C28T842OQG7NU");
    assertThat(transaction.getCardNumber()).isNull();
    assertThat(transaction.getCardPaymentText()).isNull();
  }
}
