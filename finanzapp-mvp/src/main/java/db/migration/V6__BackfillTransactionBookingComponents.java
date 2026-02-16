package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V6__BackfillTransactionBookingComponents extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    String selectSql = """
        SELECT id, raw_booking_text, partner_name, purpose_text,
               payer_name, booking_text, card_number, card_payment_text, reference_text
        FROM transactions
        """;
    String updateSql = """
        UPDATE transactions
           SET payer_name = ?,
               booking_text = ?,
               card_number = ?,
               card_payment_text = ?,
               reference_text = ?
         WHERE id = ?
        """;

    try (PreparedStatement select = connection.prepareStatement(selectSql);
         ResultSet rows = select.executeQuery();
         PreparedStatement update = connection.prepareStatement(updateSql)) {
      while (rows.next()) {
        int id = rows.getInt("id");
        String source = selectSource(rows.getString("raw_booking_text"), rows.getString("partner_name"), rows.getString("purpose_text"));
        BookingParts parsed = parseBookingParts(source);

        String currentPayer = blankToNull(rows.getString("payer_name"));
        String currentBooking = blankToNull(rows.getString("booking_text"));
        String currentCardNumber = blankToNull(rows.getString("card_number"));
        String currentCardPayment = blankToNull(rows.getString("card_payment_text"));
        String currentReference = blankToNull(rows.getString("reference_text"));

        String nextPayer = keepExistingOrUseParsed(currentPayer, parsed.payerName());
        String nextBooking = keepExistingOrUseParsed(currentBooking, parsed.bookingText());
        String nextCardNumber = keepExistingOrUseParsed(currentCardNumber, parsed.cardNumber());
        String nextCardPayment = keepExistingOrUseParsed(currentCardPayment, parsed.cardPaymentText());
        String nextReference = keepExistingOrUseParsed(currentReference, parsed.referenceText());

        if (!hasAnyChange(currentPayer, nextPayer)
            && !hasAnyChange(currentBooking, nextBooking)
            && !hasAnyChange(currentCardNumber, nextCardNumber)
            && !hasAnyChange(currentCardPayment, nextCardPayment)
            && !hasAnyChange(currentReference, nextReference)) {
          continue;
        }

        update.setString(1, nextPayer);
        update.setString(2, nextBooking);
        update.setString(3, nextCardNumber);
        update.setString(4, nextCardPayment);
        update.setString(5, nextReference);
        update.setInt(6, id);
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private String selectSource(String rawBookingText, String partnerName, String purposeText) {
    String raw = blankToNull(rawBookingText);
    if (raw != null) {
      return raw;
    }
    String partner = blankToNull(partnerName);
    String purpose = blankToNull(purposeText);
    if (partner != null && purpose != null && !containsIgnoreCase(partner, "Buchungstext:")) {
      return partner + " Buchungstext: " + purpose;
    }
    if (partner != null) {
      return partner;
    }
    if (purpose != null) {
      return "Buchungstext: " + purpose;
    }
    return "";
  }

  private BookingParts parseBookingParts(String rawBookingText) {
    if (rawBookingText == null || rawBookingText.isBlank()) {
      return new BookingParts(null, null, null, null, null);
    }

    String payerName = extractBetween(rawBookingText, "Auftraggeber:", "Buchungstext:");

    String bookingText = extractAfter(rawBookingText, "Buchungstext:");
    bookingText = trimAtMarker(bookingText, "Karte Nr.");
    bookingText = trimAtMarker(bookingText, "Ref.");

    String cardNumber = extractAfter(rawBookingText, "Karte Nr.");
    cardNumber = trimAtMarker(cardNumber, "Kartenzahlung");
    cardNumber = trimAtMarker(cardNumber, "Ref.");

    String cardPaymentText = extractAfter(rawBookingText, "Kartenzahlung");
    cardPaymentText = trimAtMarker(cardPaymentText, "Ref.");

    String referenceText = extractAfter(rawBookingText, "Ref.");

    return new BookingParts(
        blankToNull(payerName),
        blankToNull(bookingText),
        blankToNull(cardNumber),
        blankToNull(cardPaymentText),
        blankToNull(referenceText));
  }

  private String keepExistingOrUseParsed(String existingValue, String parsedValue) {
    if (existingValue != null && !existingValue.isBlank()) {
      return existingValue;
    }
    return parsedValue;
  }

  private boolean hasAnyChange(String current, String next) {
    if (current == null) {
      return next != null;
    }
    return !current.equals(next);
  }

  private String extractBetween(String text, String startMarker, String endMarker) {
    String afterStart = extractAfter(text, startMarker);
    return trimAtMarker(afterStart, endMarker);
  }

  private String extractAfter(String text, String marker) {
    if (text == null) {
      return null;
    }
    int index = indexOfIgnoreCase(text, marker);
    if (index < 0) {
      return null;
    }
    return text.substring(index + marker.length()).trim();
  }

  private String trimAtMarker(String text, String marker) {
    if (text == null) {
      return null;
    }
    int index = indexOfIgnoreCase(text, marker);
    if (index < 0) {
      return text.trim();
    }
    return text.substring(0, index).trim();
  }

  private int indexOfIgnoreCase(String text, String marker) {
    return text.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
  }

  private boolean containsIgnoreCase(String value, String fragment) {
    return indexOfIgnoreCase(value, fragment) >= 0;
  }

  private String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed;
  }

  private record BookingParts(
      String payerName,
      String bookingText,
      String cardNumber,
      String cardPaymentText,
      String referenceText) {}
}
