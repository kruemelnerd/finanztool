package com.example.finanzapp.transactions;

public record TransactionRow(
    Integer id,
    String name,
    String purpose,
    String date,
    String time,
    String status,
    String amount) {}
