package com.example.finanzapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finanzapp.domain.BalanceDaily;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.BalanceDailyRepository;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PartialsControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private BalanceDailyRepository balanceDailyRepository;

  @Autowired
  private TransactionRepository transactionRepository;

  @Test
  void balanceChartPartialRendersEmptyState() throws Exception {
    mockMvc.perform(get("/partials/balance-chart").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("No balance data available.")));
  }

  @Test
  void recentTransactionsPartialRendersEmptyState() throws Exception {
    mockMvc.perform(get("/partials/recent-transactions").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("No transactions yet.")));
  }

  @Test
  void transactionsTablePartialRendersHeaders() throws Exception {
    mockMvc.perform(get("/partials/transactions-table").with(user("user")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Name")))
        .andExpect(content().string(containsString("Date")))
        .andExpect(content().string(containsString("Amount")));
  }

  @Test
  void balanceChartRedirectsWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/partials/balance-chart"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  @Test
  void balanceChartRendersBarsWhenDataExists() throws Exception {
    User user = userRepository.findByEmail("chart-user@example.com").orElseGet(() -> {
      User created = new User();
      created.setEmail("chart-user@example.com");
      created.setPasswordHash("hashed");
      created.setLanguage("EN");
      return userRepository.save(created);
    });
    user.setLanguage("EN");
    userRepository.save(user);

    balanceDailyRepository.deleteByUser(user);
    transactionRepository.deleteByUser(user);

    BalanceDaily first = new BalanceDaily();
    first.setUser(user);
    first.setDate(LocalDate.now().minusDays(1));
    first.setBalanceCentsEndOfDay(10000L);

    BalanceDaily second = new BalanceDaily();
    second.setUser(user);
    second.setDate(LocalDate.now());
    second.setBalanceCentsEndOfDay(12000L);

    balanceDailyRepository.saveAll(java.util.List.of(first, second));

    Transaction debit = new Transaction();
    debit.setUser(user);
    debit.setBookingDateTime(LocalDateTime.now().withHour(11).withMinute(30).withSecond(0).withNano(0));
    debit.setPartnerName("Coffee Shop");
    debit.setPurposeText("Coffee");
    debit.setAmountCents(-450L);
    transactionRepository.save(debit);

    mockMvc.perform(get("/partials/balance-chart").with(user("chart-user@example.com")))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Latest balance"))))
        .andExpect(content().string(not(containsString("Balance (EUR)"))))
        .andExpect(content().string(containsString("120 EUR")))
        .andExpect(content().string(containsString("Balance: 120.00 EUR")))
        .andExpect(content().string(containsString("line-chart-marker")))
        .andExpect(content().string(containsString("Coffee Shop")));
  }

  @Test
  void balanceChartIncludesCreditBookingMarkers() throws Exception {
    User user = userRepository.findByEmail("credit-user@example.com").orElseGet(() -> {
      User created = new User();
      created.setEmail("credit-user@example.com");
      created.setPasswordHash("hashed");
      created.setLanguage("DE");
      return userRepository.save(created);
    });
    user.setLanguage("DE");
    userRepository.save(user);

    balanceDailyRepository.deleteByUser(user);
    transactionRepository.deleteByUser(user);

    BalanceDaily first = new BalanceDaily();
    first.setUser(user);
    first.setDate(LocalDate.now().minusDays(1));
    first.setBalanceCentsEndOfDay(10000L);

    BalanceDaily second = new BalanceDaily();
    second.setUser(user);
    second.setDate(LocalDate.now());
    second.setBalanceCentsEndOfDay(12000L);

    balanceDailyRepository.saveAll(java.util.List.of(first, second));

    Transaction credit = new Transaction();
    credit.setUser(user);
    credit.setBookingDateTime(LocalDateTime.now().withHour(9).withMinute(15).withSecond(0).withNano(0));
    credit.setPartnerName("Salary");
    credit.setPurposeText("Salary");
    credit.setAmountCents(99344L);
    transactionRepository.save(credit);

    mockMvc.perform(get("/partials/balance-chart").with(user("credit-user@example.com")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("line-chart-marker")))
        .andExpect(content().string(containsString("Salary")))
        .andExpect(content().string(containsString("Kontostand: 120,00 EUR")))
        .andExpect(content().string(containsString("993,44 EUR")));
  }

  @Test
  void balanceChartSplitsLineColorAtZeroThreshold() throws Exception {
    User user = userRepository.findByEmail("threshold-user@example.com").orElseGet(() -> {
      User created = new User();
      created.setEmail("threshold-user@example.com");
      created.setPasswordHash("hashed");
      created.setLanguage("EN");
      return userRepository.save(created);
    });
    user.setLanguage("EN");
    userRepository.save(user);

    balanceDailyRepository.deleteByUser(user);
    transactionRepository.deleteByUser(user);

    BalanceDaily first = new BalanceDaily();
    first.setUser(user);
    first.setDate(LocalDate.now().minusDays(1));
    first.setBalanceCentsEndOfDay(-5000L);

    BalanceDaily second = new BalanceDaily();
    second.setUser(user);
    second.setDate(LocalDate.now());
    second.setBalanceCentsEndOfDay(12000L);

    balanceDailyRepository.saveAll(java.util.List.of(first, second));

    mockMvc.perform(get("/partials/balance-chart").with(user("threshold-user@example.com")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("line-chart-line-positive")))
        .andExpect(content().string(containsString("line-chart-line-negative")))
        .andExpect(content().string(containsString("line-clip-positive")))
        .andExpect(content().string(containsString("line-clip-negative")));
  }
}
