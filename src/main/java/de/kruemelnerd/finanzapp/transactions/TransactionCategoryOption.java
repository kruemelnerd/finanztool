package de.kruemelnerd.finanzapp.transactions;

public record TransactionCategoryOption(
    Integer id,
    String label,
    boolean defaultCategory) {}
