package com.example.finanzapp.importcsv;

import com.example.finanzapp.domain.Transaction;
import java.util.List;

public record CsvParsingResult(
    Long startBalanceCents,
    Long currentBalanceCents,
    List<Transaction> transactions) {}
