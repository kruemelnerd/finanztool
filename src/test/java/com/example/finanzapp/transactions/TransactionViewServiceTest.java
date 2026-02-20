package com.example.finanzapp.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.finanzapp.balance.AccountBalanceService;
import com.example.finanzapp.categories.CategoryBootstrapService;
import com.example.finanzapp.domain.Category;
import com.example.finanzapp.domain.CategoryAssignedBy;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.CategoryRepository;
import com.example.finanzapp.repository.RuleRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class TransactionViewServiceTest {
  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private AccountBalanceService accountBalanceService;

  @Mock
  private CategoryBootstrapService categoryBootstrapService;

  @Mock
  private CategoryRepository categoryRepository;

  @Mock
  private RuleRepository ruleRepository;

  private TransactionViewService service;

  @BeforeEach
  void setUp() {
    service = new TransactionViewService(
        transactionRepository,
        userRepository,
        accountBalanceService,
        categoryBootstrapService,
        categoryRepository,
        ruleRepository);

    lenient().when(categoryRepository.findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
  }

  @Test
  void loadTransactionsAppliesMinAndMaxFilters() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("EN");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    Transaction low = buildTransaction(user, LocalDateTime.of(2026, 2, 1, 9, 0), -2000L, "LOW");
    Transaction mid = buildTransaction(user, LocalDateTime.of(2026, 2, 2, 9, 0), -7500L, "MID");
    Transaction high = buildTransaction(user, LocalDateTime.of(2026, 2, 3, 9, 0), -12000L, "HIGH");

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of(high, mid, low));

    List<TransactionRow> rows = service.loadTransactions(
        principal,
        new BigDecimal("50"),
        new BigDecimal("100"),
        null,
        null);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).name()).isEqualTo("MID");
    assertThat(rows.get(0).amount()).isEqualTo("-75.00 EUR");
    assertThat(rows.get(0).date()).isEqualTo("2026-02-02");
  }

  @Test
  void loadTransactionsPageReturnsSliceAndMetadata() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    List<Transaction> transactions = java.util.stream.IntStream.rangeClosed(1, 12)
        .mapToObj(i -> buildTransaction(user, LocalDateTime.of(2026, 2, i, 9, 0), -100L * i, "TX-" + i))
        .sorted((a, b) -> b.getBookingDateTime().compareTo(a.getBookingDateTime()))
        .toList();

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(transactions);

    TransactionPage firstPage = service.loadTransactionsPage(principal, null, null, null, null, false, 0, 10);
    TransactionPage secondPage = service.loadTransactionsPage(principal, null, null, null, null, false, 1, 10);

    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.totalItems()).isEqualTo(12);
    assertThat(firstPage.rows()).hasSize(10);
    assertThat(firstPage.rows().get(0).name()).isEqualTo("TX-12");
    assertThat(firstPage.rows().get(9).name()).isEqualTo("TX-3");

    assertThat(secondPage.rows()).hasSize(2);
    assertThat(secondPage.rows().get(0).name()).isEqualTo("TX-2");
    assertThat(secondPage.rows().get(1).name()).isEqualTo("TX-1");
  }

  @Test
  void loadTransactionsPageCanFilterOnlyDefaultAssignedRows() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    Transaction defaultTx = buildTransaction(user, LocalDateTime.of(2026, 2, 2, 9, 0), -100L, "DEFAULT");
    defaultTx.setCategoryAssignedBy(CategoryAssignedBy.DEFAULT);

    Transaction manualTx = buildTransaction(user, LocalDateTime.of(2026, 2, 1, 9, 0), -100L, "MANUAL");
    manualTx.setCategoryAssignedBy(CategoryAssignedBy.MANUAL);

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of(defaultTx, manualTx));

    TransactionPage page = service.loadTransactionsPage(principal, null, null, null, null, true, 0, 10);

    assertThat(page.rows()).hasSize(1);
    assertThat(page.rows().get(0).name()).isEqualTo("DEFAULT");
  }

  @Test
  void loadTransactionsFormatsDateAndAmountForGermanLanguage() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("DE");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    Transaction tx = buildTransaction(user, LocalDateTime.of(2026, 2, 2, 9, 0), -123456L, "Miete");

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of(tx));

    List<TransactionRow> rows = service.loadTransactions(principal, null, null, null, null);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).date()).isEqualTo("02.02.2026");
    assertThat(rows.get(0).amount()).isEqualTo("-1.234,56 EUR");
  }

  private Transaction buildTransaction(User user, LocalDateTime dateTime, long cents, String name) {
    Transaction tx = new Transaction();
    tx.setUser(user);
    tx.setBookingDateTime(dateTime);
    tx.setPartnerName(name);
    tx.setPurposeText("Purpose");
    tx.setAmountCents(cents);
    return tx;
  }

  @Test
  void loadCurrentBalanceUsesNewestCsvMetaBalance() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("EN");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(accountBalanceService.computeCurrentBalanceCents(eq(user))).thenReturn(Optional.of(33478L));

    String currentBalance = service.loadCurrentBalanceLabel(principal);

    assertThat(currentBalance).isEqualTo("334.78 EUR");
  }

  @Test
  void loadCurrentBalanceUsesGermanAmountFormatting() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");
    user.setLanguage("DE");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(accountBalanceService.computeCurrentBalanceCents(eq(user))).thenReturn(Optional.of(123456L));

    String currentBalance = service.loadCurrentBalanceLabel(principal);

    assertThat(currentBalance).isEqualTo("1.234,56 EUR");
  }

  @Test
  void loadTransactionsAppliesContainsFiltersCaseInsensitive() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    Transaction first = buildTransaction(user, LocalDateTime.of(2026, 2, 1, 9, 0), -2000L, "Alex");
    first.setPurposeText("Space Marine 2 Ref. DL5C28T842OQG7NU");
    Transaction second = buildTransaction(user, LocalDateTime.of(2026, 2, 2, 9, 0), -7500L, "Rent");
    second.setPurposeText("Apartment");

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of(first, second));

    List<TransactionRow> rows = service.loadTransactions(
        principal,
        null,
        null,
        " al ",
        "spac");

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).name()).isEqualTo("Alex");
  }

  @Test
  void softDeleteTransactionMarksRowAsDeletedForUser() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.softDeleteByIdAndUser(eq(42), eq(user), org.mockito.ArgumentMatchers.any()))
        .thenReturn(1);

    boolean deleted = service.softDeleteTransaction(principal, 42);

    assertThat(deleted).isTrue();
  }

  @Test
  void setManualCategoryStoresManualAssignmentAndLock() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    Transaction tx = buildTransaction(user, LocalDateTime.of(2026, 2, 2, 9, 0), -100L, "TX");

    Category parent = new Category();
    parent.setName("Shopping");
    Category sub = new Category();
    sub.setName("Sport");
    sub.setParent(parent);

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.findByIdAndUserAndDeletedAtIsNull(9, user)).thenReturn(Optional.of(tx));
    when(categoryRepository.findByIdAndUserAndDeletedAtIsNull(22, user)).thenReturn(Optional.of(sub));

    boolean updated = service.setManualCategory(principal, 9, 22);

    assertThat(updated).isTrue();
    assertThat(tx.getCategory()).isEqualTo(sub);
    assertThat(tx.getCategoryAssignedBy()).isEqualTo(CategoryAssignedBy.MANUAL);
    assertThat(tx.isCategoryLocked()).isTrue();
    assertThat(tx.getRuleConflicts()).isNull();
  }

  @Test
  void loadTransactionsSanitizesLegacyPurposeDisplay() {
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    UserDetails principal = org.springframework.security.core.userdetails.User
        .withUsername("user@example.com")
        .password("hashed")
        .roles("USER")
        .build();

    Transaction tx = buildTransaction(user, LocalDateTime.of(2026, 2, 6, 0, 0), -2828L, "NETFLIX");
    tx.setPartnerName("H&M Buchungstext: H&M, Berlin DE Karte Nr. 4871 78XX XXXX 8491");
    tx.setPurposeText(
        "Netflix.com Buchungstext: Netflix.com, Amsterdam DE Karte Nr. 4871 78XX XXXX 8491 Kartenzahlung");

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(transactionRepository.findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(eq(user)))
        .thenReturn(List.of(tx));

    List<TransactionRow> rows = service.loadTransactions(principal, null, null, null, null);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).name()).isEqualTo("H&M");
    assertThat(rows.get(0).purpose()).isEqualTo("Netflix.com, Amsterdam DE");
  }
}
