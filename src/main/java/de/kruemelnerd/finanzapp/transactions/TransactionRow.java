package de.kruemelnerd.finanzapp.transactions;

public record TransactionRow(
    Integer id,
    String name,
    String purpose,
    String date,
    String time,
    String status,
    String amount,
    Integer categoryId,
    String categoryLabel,
    boolean defaultCategory,
    String assignedBy,
    boolean categoryLocked,
    String conflictNames) {}
