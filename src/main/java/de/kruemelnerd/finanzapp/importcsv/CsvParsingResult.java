package de.kruemelnerd.finanzapp.importcsv;

import de.kruemelnerd.finanzapp.domain.Transaction;
import java.util.List;

public record CsvParsingResult(
    Long startBalanceCents,
    Long currentBalanceCents,
    List<Transaction> transactions) {}
