package de.kruemelnerd.finanzapp.balance;

import java.time.LocalDate;

public record BalancePoint(LocalDate date, long balanceCents) {}
