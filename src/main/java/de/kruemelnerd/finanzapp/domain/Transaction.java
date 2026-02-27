package de.kruemelnerd.finanzapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "booking_datetime", nullable = false)
  private LocalDateTime bookingDateTime;

  @Column(name = "value_date")
  private LocalDate valueDate;

  @Column(name = "transaction_type")
  private String transactionType;

  @Column(name = "partner_name", nullable = false)
  private String partnerName;

  @Column(name = "purpose_text", nullable = false)
  private String purposeText;

  @Column(name = "raw_booking_text")
  private String rawBookingText;

  @Column(name = "payer_name")
  private String payerName;

  @Column(name = "booking_text")
  private String bookingText;

  @Column(name = "card_number")
  private String cardNumber;

  @Column(name = "card_payment_text")
  private String cardPaymentText;

  @Column(name = "reference_text")
  private String referenceText;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  @Enumerated(EnumType.STRING)
  @Column(name = "category_assigned_by")
  private CategoryAssignedBy categoryAssignedBy;

  @Column(name = "category_locked", nullable = false)
  private boolean categoryLocked;

  @Column(name = "rule_conflicts")
  private String ruleConflicts;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(nullable = false)
  private String currency = "EUR";

  @Column(nullable = false)
  private String status = "Completed";

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (currency == null) {
      currency = "EUR";
    }
    if (status == null) {
      status = "Completed";
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

  public LocalDateTime getBookingDateTime() {
    return bookingDateTime;
  }

  public void setBookingDateTime(LocalDateTime bookingDateTime) {
    this.bookingDateTime = bookingDateTime;
  }

  public LocalDate getValueDate() {
    return valueDate;
  }

  public void setValueDate(LocalDate valueDate) {
    this.valueDate = valueDate;
  }

  public String getTransactionType() {
    return transactionType;
  }

  public void setTransactionType(String transactionType) {
    this.transactionType = transactionType;
  }

  public String getPartnerName() {
    return partnerName;
  }

  public void setPartnerName(String partnerName) {
    this.partnerName = partnerName;
  }

  public String getPurposeText() {
    return purposeText;
  }

  public void setPurposeText(String purposeText) {
    this.purposeText = purposeText;
  }

  public String getRawBookingText() {
    return rawBookingText;
  }

  public void setRawBookingText(String rawBookingText) {
    this.rawBookingText = rawBookingText;
  }

  public String getPayerName() {
    return payerName;
  }

  public void setPayerName(String payerName) {
    this.payerName = payerName;
  }

  public String getBookingText() {
    return bookingText;
  }

  public void setBookingText(String bookingText) {
    this.bookingText = bookingText;
  }

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public String getCardPaymentText() {
    return cardPaymentText;
  }

  public void setCardPaymentText(String cardPaymentText) {
    this.cardPaymentText = cardPaymentText;
  }

  public String getReferenceText() {
    return referenceText;
  }

  public void setReferenceText(String referenceText) {
    this.referenceText = referenceText;
  }

  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }

  public CategoryAssignedBy getCategoryAssignedBy() {
    return categoryAssignedBy;
  }

  public void setCategoryAssignedBy(CategoryAssignedBy categoryAssignedBy) {
    this.categoryAssignedBy = categoryAssignedBy;
  }

  public boolean isCategoryLocked() {
    return categoryLocked;
  }

  public void setCategoryLocked(boolean categoryLocked) {
    this.categoryLocked = categoryLocked;
  }

  public String getRuleConflicts() {
    return ruleConflicts;
  }

  public void setRuleConflicts(String ruleConflicts) {
    this.ruleConflicts = ruleConflicts;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public void setAmountCents(long amountCents) {
    this.amountCents = amountCents;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
