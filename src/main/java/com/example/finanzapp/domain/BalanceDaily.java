package com.example.finanzapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "balance_daily")
public class BalanceDaily {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private LocalDate date;

  @Column(name = "balance_cents_end_of_day", nullable = false)
  private long balanceCentsEndOfDay;

  @Column(nullable = false)
  private String currency = "EUR";

  @Column(name = "computed_at", nullable = false)
  private Instant computedAt;

  @PrePersist
  void onCreate() {
    if (computedAt == null) {
      computedAt = Instant.now();
    }
    if (currency == null) {
      currency = "EUR";
    }
  }

  public Integer getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public long getBalanceCentsEndOfDay() {
    return balanceCentsEndOfDay;
  }

  public void setBalanceCentsEndOfDay(long balanceCentsEndOfDay) {
    this.balanceCentsEndOfDay = balanceCentsEndOfDay;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Instant getComputedAt() {
    return computedAt;
  }
}
