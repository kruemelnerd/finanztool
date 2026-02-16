package com.example.finanzapp.importcsv;

import java.util.List;

public record CsvImportResult(
    int importedCount,
    int duplicateCount,
    List<String> duplicateSamples) {}
