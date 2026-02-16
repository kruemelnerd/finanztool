package com.example.finanzapp.partials;

import com.example.finanzapp.balance.BalancePoint;
import com.example.finanzapp.balance.AccountBalanceService;
import com.example.finanzapp.balance.BalanceService;
import com.example.finanzapp.domain.Transaction;
import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.TransactionRepository;
import com.example.finanzapp.repository.UserRepository;
import com.example.finanzapp.transactions.TransactionPage;
import com.example.finanzapp.transactions.TransactionRow;
import com.example.finanzapp.transactions.TransactionViewService;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PartialsController {
  private static final int PAGE_SIZE = 10;
  private static final int CHART_WIDTH = 860;
  private static final int CHART_HEIGHT = 230;
  private static final int CHART_PAD_TOP = 16;
  private static final int CHART_PAD_BOTTOM = 26;
  private static final int CHART_PAD_LEFT = 92;
  private static final int CHART_PAD_RIGHT = 14;
  private static final int CHART_MIN_TOOLTIP_WIDTH = 210;
  private static final int CHART_MAX_TOOLTIP_WIDTH = 360;

  private final TransactionViewService transactionViewService;
  private final AccountBalanceService accountBalanceService;
  private final BalanceService balanceService;
  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;

  public PartialsController(
      TransactionViewService transactionViewService,
      AccountBalanceService accountBalanceService,
      BalanceService balanceService,
      UserRepository userRepository,
      TransactionRepository transactionRepository) {
    this.transactionViewService = transactionViewService;
    this.accountBalanceService = accountBalanceService;
    this.balanceService = balanceService;
    this.userRepository = userRepository;
    this.transactionRepository = transactionRepository;
  }

  @GetMapping("/partials/recent-transactions")
  public String recentTransactions(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(name = "limit", defaultValue = "5") int limit,
      Model model) {
    List<TransactionRow> rows = transactionViewService.loadRecent(userDetails, limit);
    model.addAttribute("transactions", rows);
    model.addAttribute("transactionsEmpty", rows.isEmpty());
    return "partials/recent-transactions";
  }

  @GetMapping("/partials/transactions-table")
  public String transactionsTable(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
      @RequestParam(name = "maxAmount", required = false) BigDecimal maxAmount,
      @RequestParam(name = "nameContains", required = false) String nameContains,
      @RequestParam(name = "purposeContains", required = false) String purposeContains,
      @RequestParam(name = "page", required = false) Integer page,
      Model model) {
    TransactionPage transactionPage = transactionViewService.loadTransactionsPage(
        userDetails,
        minAmount,
        maxAmount,
        nameContains,
        purposeContains,
        page,
        PAGE_SIZE);
    model.addAttribute("transactions", transactionPage.rows());
    model.addAttribute("transactionsEmpty", transactionPage.rows().isEmpty());
    model.addAttribute("currentPage", transactionPage.page());
    model.addAttribute("totalPages", transactionPage.totalPages());
    model.addAttribute("totalItems", transactionPage.totalItems());
    model.addAttribute("hasPreviousPage", transactionPage.hasPreviousPage());
    model.addAttribute("hasNextPage", transactionPage.hasNextPage());
    model.addAttribute("previousPage", transactionPage.previousPage());
    model.addAttribute("nextPage", transactionPage.nextPage());
    model.addAttribute("minAmount", minAmount);
    model.addAttribute("maxAmount", maxAmount);
    model.addAttribute("nameContains", nameContains);
    model.addAttribute("purposeContains", purposeContains);
    return "partials/transactions-table";
  }

  @GetMapping("/partials/balance-chart")
  public String balanceChart(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(name = "range", defaultValue = "30d") String range,
      Model model) {
    Optional<User> user = resolveUser(userDetails);
    if (user.isEmpty()) {
      model.addAttribute("chartEmpty", true);
      return "partials/balance-chart";
    }

    int days = resolveRangeDays(range);
    LocalDate end = LocalDate.now();
    LocalDate start = end.minusDays(days - 1L);
    List<BalancePoint> points = accountBalanceService.computeRange(user.get(), start, end);
    if (points.isEmpty()) {
      points = balanceService.loadRange(user.get(), start, end);
    }
    if (points.isEmpty()) {
      model.addAttribute("chartEmpty", true);
      return "partials/balance-chart";
    }

    Locale locale = resolveLocale(user.get());
    long max = points.stream()
        .mapToLong(BalancePoint::balanceCents)
        .max()
        .orElse(0L);
    long min = points.stream()
        .mapToLong(BalancePoint::balanceCents)
        .min()
        .orElse(0L);

    ChartShape shape = toChartShape(points, min, max);
    DateTimeFormatter labelFormatter = resolveChartDateFormatter(locale);
    List<ChartAxisTick> yAxisTicks = toYAxisTicks(min, max, locale);
    List<ChartDebitMarker> debitMarkers = toDebitMarkers(user.get(), start, end, points, shape, locale);

    model.addAttribute("chartLinePoints", shape.linePoints());
    model.addAttribute("chartAreaPoints", shape.areaPoints());
    model.addAttribute("chartYAxisTicks", yAxisTicks);
    model.addAttribute("chartPlotStartX", CHART_PAD_LEFT);
    model.addAttribute("chartPlotEndX", CHART_WIDTH - CHART_PAD_RIGHT);
    model.addAttribute("chartDebitMarkers", debitMarkers);
    model.addAttribute("chartTickStart", labelFormatter.format(points.get(0).date()));
    model.addAttribute("chartTickMiddle", labelFormatter.format(points.get(points.size() / 2).date()));
    model.addAttribute("chartTickEnd", labelFormatter.format(points.get(points.size() - 1).date()));
    model.addAttribute("chartLatestAmount", formatAmount(points.get(points.size() - 1).balanceCents(), locale));
    model.addAttribute("chartEmpty", false);
    return "partials/balance-chart";
  }

  private Optional<User> resolveUser(UserDetails userDetails) {
    if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(userDetails.getUsername());
  }

  private Locale resolveLocale(User user) {
    if ("DE".equalsIgnoreCase(user.getLanguage())) {
      return Locale.GERMANY;
    }
    return Locale.ENGLISH;
  }

  private ChartShape toChartShape(List<BalancePoint> points, long min, long max) {
    long range = Math.max(1L, max - min);
    List<ChartPoint> coordinates = new ArrayList<>();

    for (int i = 0; i < points.size(); i++) {
      BalancePoint point = points.get(i);
      double x = points.size() == 1
          ? CHART_PAD_LEFT
          : CHART_PAD_LEFT + (chartPlotWidth() * i / (double) (points.size() - 1));
      double y = CHART_PAD_TOP + ((max - point.balanceCents()) / (double) range) * chartPlotHeight();
      int roundedX = round(x);
      int roundedY = round(y);
      coordinates.add(new ChartPoint(roundedX, roundedY));
    }

    String linePath = toSmoothLinePath(coordinates);
    String areaPath = toAreaPath(coordinates, linePath);
    return new ChartShape(linePath, areaPath, coordinates);
  }

  private String toSmoothLinePath(List<ChartPoint> coordinates) {
    if (coordinates.isEmpty()) {
      return "";
    }
    if (coordinates.size() == 1) {
      ChartPoint only = coordinates.get(0);
      return "M " + only.x() + "," + only.y();
    }

    StringBuilder path = new StringBuilder();
    ChartPoint first = coordinates.get(0);
    path.append("M ").append(first.x()).append(',').append(first.y());

    for (int i = 0; i < coordinates.size() - 1; i++) {
      ChartPoint p0 = i > 0 ? coordinates.get(i - 1) : coordinates.get(i);
      ChartPoint p1 = coordinates.get(i);
      ChartPoint p2 = coordinates.get(i + 1);
      ChartPoint p3 = i + 2 < coordinates.size() ? coordinates.get(i + 2) : p2;

      double cp1x = p1.x() + (p2.x() - p0.x()) / 6.0d;
      double cp1y = p1.y() + (p2.y() - p0.y()) / 6.0d;
      double cp2x = p2.x() - (p3.x() - p1.x()) / 6.0d;
      double cp2y = p2.y() - (p3.y() - p1.y()) / 6.0d;

      path.append(" C ")
          .append(svg(cp1x)).append(',').append(svg(cp1y)).append(' ')
          .append(svg(cp2x)).append(',').append(svg(cp2y)).append(' ')
          .append(p2.x()).append(',').append(p2.y());
    }
    return path.toString();
  }

  private String toAreaPath(List<ChartPoint> coordinates, String linePath) {
    if (coordinates.isEmpty()) {
      return "";
    }
    int baselineY = CHART_HEIGHT - CHART_PAD_BOTTOM;
    ChartPoint first = coordinates.get(0);
    ChartPoint last = coordinates.get(coordinates.size() - 1);
    return linePath
        + " L " + last.x() + "," + baselineY
        + " L " + first.x() + "," + baselineY
        + " Z";
  }

  private String svg(double value) {
    if (Math.abs(value - Math.rint(value)) < 0.001d) {
      return Integer.toString((int) Math.rint(value));
    }
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private List<ChartAxisTick> toYAxisTicks(long min, long max, Locale locale) {
    int tickCount = 5;
    long range = Math.max(1L, max - min);
    List<ChartAxisTick> ticks = new ArrayList<>();
    for (int i = 0; i < tickCount; i++) {
      double ratio = i / (double) (tickCount - 1);
      long value = Math.round(max - (range * ratio));
      int y = round(CHART_PAD_TOP + ratio * chartPlotHeight());
      ticks.add(new ChartAxisTick(y, formatAmount(value, locale)));
    }
    return ticks;
  }

  private List<ChartDebitMarker> toDebitMarkers(
      User user,
      LocalDate start,
      LocalDate end,
      List<BalancePoint> points,
      ChartShape shape,
      Locale locale) {
    List<Transaction> debits = transactionRepository.findDebitsInRange(
        user,
        start.atStartOfDay(),
        end.plusDays(1).atStartOfDay());
    if (debits.isEmpty()) {
      return List.of();
    }

    Map<LocalDate, List<Transaction>> debitsByDate = debits.stream()
        .collect(Collectors.groupingBy(
            tx -> tx.getBookingDateTime().toLocalDate(),
            LinkedHashMap::new,
            Collectors.toList()));

    Map<LocalDate, ChartPoint> pointByDate = new LinkedHashMap<>();
    Map<LocalDate, Integer> pointIndexByDate = new LinkedHashMap<>();
    for (int i = 0; i < points.size() && i < shape.coordinates().size(); i++) {
      pointByDate.put(points.get(i).date(), shape.coordinates().get(i));
      pointIndexByDate.put(points.get(i).date(), i);
    }

    DateTimeFormatter dayFormatter = resolveChartDayFormatter(locale);
    DateTimeFormatter timeFormatter = resolveChartTimeFormatter(locale);
    List<ChartDebitMarker> markers = new ArrayList<>();

    for (Map.Entry<LocalDate, List<Transaction>> entry : debitsByDate.entrySet()) {
      ChartPoint point = resolveMarkerPoint(entry.getKey(), pointByDate, pointIndexByDate, shape.coordinates());
      if (point == null) {
        continue;
      }

      List<String> lines = entry.getValue().stream()
          .map(tx -> formatDebitTooltipLine(tx, locale, timeFormatter))
          .toList();
      int tooltipWidth = resolveTooltipWidth(lines);
      int tooltipHeight = 34 + lines.size() * 16;
      int tooltipOffsetX = point.x() > CHART_WIDTH - tooltipWidth - 20 ? -tooltipWidth - 14 : 14;
      int preferredOffsetY = -tooltipHeight - 10;
      int minOffsetY = 8 - point.y();
      int tooltipOffsetY = Math.max(preferredOffsetY, minOffsetY);

      markers.add(new ChartDebitMarker(
          point.x(),
          point.y(),
          tooltipOffsetX,
          tooltipOffsetY,
          tooltipWidth,
          tooltipHeight,
          dayFormatter.format(entry.getKey()),
          lines));
    }
    return markers;
  }

  private ChartPoint resolveMarkerPoint(
      LocalDate bookingDate,
      Map<LocalDate, ChartPoint> pointByDate,
      Map<LocalDate, Integer> pointIndexByDate,
      List<ChartPoint> coordinates) {
    Integer index = pointIndexByDate.get(bookingDate);
    if (index == null) {
      return null;
    }
    if (index <= 0) {
      return pointByDate.get(bookingDate);
    }
    return coordinates.get(index - 1);
  }

  private int resolveTooltipWidth(List<String> lines) {
    int longestLineLength = lines.stream().mapToInt(String::length).max().orElse(0);
    int estimated = 44 + (longestLineLength * 7);
    return Math.max(CHART_MIN_TOOLTIP_WIDTH, Math.min(CHART_MAX_TOOLTIP_WIDTH, estimated));
  }

  private String formatDebitTooltipLine(Transaction tx, Locale locale, DateTimeFormatter timeFormatter) {
    String partner = tx.getPartnerName();
    if (partner == null || partner.isBlank()) {
      partner = tx.getPurposeText();
    }
    return timeFormatter.format(tx.getBookingDateTime())
        + "  " + partner
        + "  " + formatAmount(tx.getAmountCents(), locale);
  }

  private double chartPlotWidth() {
    return CHART_WIDTH - (CHART_PAD_LEFT + CHART_PAD_RIGHT);
  }

  private double chartPlotHeight() {
    return CHART_HEIGHT - (CHART_PAD_TOP + CHART_PAD_BOTTOM);
  }

  private DateTimeFormatter resolveChartDateFormatter(Locale locale) {
    if (Locale.GERMAN.getLanguage().equals(locale.getLanguage())) {
      return DateTimeFormatter.ofPattern("dd.MM", locale);
    }
    return DateTimeFormatter.ofPattern("MMM dd", locale);
  }

  private DateTimeFormatter resolveChartDayFormatter(Locale locale) {
    if (Locale.GERMAN.getLanguage().equals(locale.getLanguage())) {
      return DateTimeFormatter.ofPattern("dd.MM.yyyy", locale);
    }
    return DateTimeFormatter.ofPattern("MMM dd, yyyy", locale);
  }

  private DateTimeFormatter resolveChartTimeFormatter(Locale locale) {
    if (Locale.GERMAN.getLanguage().equals(locale.getLanguage())) {
      return DateTimeFormatter.ofPattern("HH:mm", locale);
    }
    return DateTimeFormatter.ofPattern("hh:mm a", locale);
  }

  private int round(double value) {
    return (int) Math.round(value);
  }

  private int resolveRangeDays(String range) {
    return 30;
  }

  private String formatAmount(long cents, Locale locale) {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
    DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
    format.setGroupingUsed(true);
    return format.format(cents / 100.0d) + " EUR";
  }

  private record ChartShape(String linePoints, String areaPoints, List<ChartPoint> coordinates) {}

  private record ChartPoint(int x, int y) {}

  private record ChartAxisTick(int y, String label) {}

  private record ChartDebitMarker(
      int x,
      int y,
      int tooltipOffsetX,
      int tooltipOffsetY,
      int tooltipWidth,
      int tooltipHeight,
      String dayLabel,
      List<String> lines) {}
}
