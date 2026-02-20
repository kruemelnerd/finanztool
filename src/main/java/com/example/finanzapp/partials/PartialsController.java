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
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

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
  private final MessageSource messageSource;

  public PartialsController(
      TransactionViewService transactionViewService,
      AccountBalanceService accountBalanceService,
      BalanceService balanceService,
      UserRepository userRepository,
      TransactionRepository transactionRepository,
      MessageSource messageSource) {
    this.transactionViewService = transactionViewService;
    this.accountBalanceService = accountBalanceService;
    this.balanceService = balanceService;
    this.userRepository = userRepository;
    this.transactionRepository = transactionRepository;
    this.messageSource = messageSource;
  }

  @GetMapping("/partials/recent-transactions")
  public String recentTransactions(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(name = "limit", defaultValue = "5") int limit,
      Model model) {
    int safeLimit = Math.max(1, limit);
    try {
      List<TransactionRow> rows = transactionViewService.loadRecent(userDetails, safeLimit);
      model.addAttribute("transactions", rows);
      model.addAttribute("transactionsEmpty", rows.isEmpty());
      model.addAttribute("partialError", false);
    } catch (RuntimeException ex) {
      model.addAttribute("transactions", List.of());
      model.addAttribute("transactionsEmpty", true);
      model.addAttribute("partialError", true);
      model.addAttribute("partialErrorMessage", msg("partial.error.generic"));
      model.addAttribute("partialRetryPath", "/partials/recent-transactions?limit=" + safeLimit);
    }
    return "partials/recent-transactions";
  }

  @GetMapping("/partials/transactions-table")
  public String transactionsTable(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
      @RequestParam(name = "maxAmount", required = false) BigDecimal maxAmount,
      @RequestParam(name = "nameContains", required = false) String nameContains,
      @RequestParam(name = "purposeContains", required = false) String purposeContains,
      @RequestParam(name = "onlyUncategorized", required = false, defaultValue = "false") boolean onlyUncategorized,
      @RequestParam(name = "page", required = false) Integer page,
      Model model) {
    try {
      TransactionPage transactionPage = transactionViewService.loadTransactionsPage(
          userDetails,
          minAmount,
          maxAmount,
          nameContains,
          purposeContains,
          onlyUncategorized,
          page,
          PAGE_SIZE);
      model.addAttribute("transactions", transactionPage.rows());
      model.addAttribute("transactionsEmpty", transactionPage.rows().isEmpty());
      model.addAttribute("categoryOptions", transactionViewService.loadCategoryOptions(userDetails));
      model.addAttribute("currentPage", transactionPage.page());
      model.addAttribute("totalPages", transactionPage.totalPages());
      model.addAttribute("totalItems", transactionPage.totalItems());
      model.addAttribute("hasPreviousPage", transactionPage.hasPreviousPage());
      model.addAttribute("hasNextPage", transactionPage.hasNextPage());
      model.addAttribute("previousPage", transactionPage.previousPage());
      model.addAttribute("nextPage", transactionPage.nextPage());
      model.addAttribute("partialError", false);
    } catch (RuntimeException ex) {
      model.addAttribute("transactions", List.of());
      model.addAttribute("transactionsEmpty", true);
      model.addAttribute("currentPage", 0);
      model.addAttribute("totalPages", 1);
      model.addAttribute("totalItems", 0);
      model.addAttribute("hasPreviousPage", false);
      model.addAttribute("hasNextPage", false);
      model.addAttribute("previousPage", 0);
      model.addAttribute("nextPage", 0);
      model.addAttribute("partialError", true);
      model.addAttribute("partialErrorMessage", msg("partial.error.generic"));
      model.addAttribute(
          "partialRetryPath",
          UriComponentsBuilder.fromPath("/partials/transactions-table")
              .queryParam("minAmount", minAmount)
              .queryParam("maxAmount", maxAmount)
              .queryParam("nameContains", nameContains)
              .queryParam("purposeContains", purposeContains)
              .queryParam("onlyUncategorized", onlyUncategorized)
              .queryParam("page", page)
              .build()
              .toUriString());
    }
    model.addAttribute("minAmount", minAmount);
    model.addAttribute("maxAmount", maxAmount);
    model.addAttribute("nameContains", nameContains);
    model.addAttribute("purposeContains", purposeContains);
    model.addAttribute("onlyUncategorized", onlyUncategorized);
    return "partials/transactions-table";
  }

  @GetMapping("/partials/balance-chart")
  public String balanceChart(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(name = "range", defaultValue = "30d") String range,
      Model model) {
    String chartRange = range == null || range.isBlank() ? "30d" : range;
    model.addAttribute("chartRange", chartRange);
    try {
      Optional<User> user = resolveUser(userDetails);
      if (user.isEmpty()) {
        model.addAttribute("chartEmpty", true);
        model.addAttribute("chartError", false);
        return "partials/balance-chart";
      }

      int days = resolveRangeDays(chartRange);
      LocalDate end = LocalDate.now();
      LocalDate start = end.minusDays(days - 1L);
      List<BalancePoint> points = accountBalanceService.computeRange(user.get(), start, end);
      if (points.isEmpty()) {
        points = balanceService.loadRange(user.get(), start, end);
      }
      if (points.isEmpty()) {
        model.addAttribute("chartEmpty", true);
        model.addAttribute("chartError", false);
        return "partials/balance-chart";
      }

      Locale locale = resolveLocale(user.get());
      long rawMax = points.stream()
          .mapToLong(BalancePoint::balanceCents)
          .max()
          .orElse(0L);
      long rawMin = points.stream()
          .mapToLong(BalancePoint::balanceCents)
          .min()
          .orElse(0L);
      ChartScale chartScale = resolveChartScale(rawMin, rawMax);

      ChartShape shape = toChartShape(points, chartScale.minCents(), chartScale.maxCents());
      DateTimeFormatter labelFormatter = resolveChartDateFormatter(locale);
      List<ChartAxisTick> yAxisTicks = toYAxisTicks(chartScale, locale);
      List<ChartDebitMarker> debitMarkers = toDebitMarkers(user.get(), start, end, points, shape, locale);

      model.addAttribute("chartLinePoints", shape.linePoints());
      model.addAttribute("chartAreaPoints", shape.areaPoints());
      model.addAttribute("chartYAxisTicks", yAxisTicks);
      model.addAttribute("chartPlotStartX", CHART_PAD_LEFT);
      model.addAttribute("chartPlotEndX", CHART_WIDTH - CHART_PAD_RIGHT);
      model.addAttribute("chartPlotTopY", CHART_PAD_TOP);
      model.addAttribute("chartPlotBottomY", CHART_HEIGHT - CHART_PAD_BOTTOM);
      model.addAttribute("chartPlotWidth", round(chartPlotWidth()));
      model.addAttribute("chartZeroY", resolveZeroY(chartScale.minCents(), chartScale.maxCents()));
      model.addAttribute("chartDebitMarkers", debitMarkers);
      model.addAttribute("chartTickStart", labelFormatter.format(points.get(0).date()));
      model.addAttribute("chartTickMiddle", labelFormatter.format(points.get(points.size() / 2).date()));
      model.addAttribute("chartTickEnd", labelFormatter.format(points.get(points.size() - 1).date()));
      model.addAttribute("chartEmpty", false);
      model.addAttribute("chartError", false);
    } catch (RuntimeException ex) {
      model.addAttribute("chartEmpty", false);
      model.addAttribute("chartError", true);
      model.addAttribute("chartErrorMessage", msg("partial.error.chart"));
      model.addAttribute("chartRetryPath", "/partials/balance-chart?range=" + chartRange);
    }
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
    List<ChartPoint> coordinates = new ArrayList<>();

    for (int i = 0; i < points.size(); i++) {
      BalancePoint point = points.get(i);
      double x = points.size() == 1
          ? CHART_PAD_LEFT
          : CHART_PAD_LEFT + (chartPlotWidth() * i / (double) (points.size() - 1));
      int roundedX = round(x);
      int roundedY = toChartY(point.balanceCents(), min, max);
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

  private List<ChartAxisTick> toYAxisTicks(ChartScale chartScale, Locale locale) {
    List<ChartAxisTick> ticks = new ArrayList<>();
    long step = chartScale.tickStepCents();
    int tickCount = (int) ((chartScale.maxCents() - chartScale.minCents()) / step) + 1;
    for (int i = 0; i < tickCount; i++) {
      long value = chartScale.maxCents() - (i * step);
      int y = toChartY(value, chartScale.minCents(), chartScale.maxCents());
      ticks.add(new ChartAxisTick(y, formatAxisAmount(value, locale)));
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
    Map<LocalDate, Long> balanceByDate = new LinkedHashMap<>();
    for (int i = 0; i < points.size() && i < shape.coordinates().size(); i++) {
      pointByDate.put(points.get(i).date(), shape.coordinates().get(i));
      balanceByDate.put(points.get(i).date(), points.get(i).balanceCents());
    }

    DateTimeFormatter dayFormatter = resolveChartDayFormatter(locale);
    DateTimeFormatter timeFormatter = resolveChartTimeFormatter(locale);
    List<ChartDebitMarker> markers = new ArrayList<>();

    for (Map.Entry<LocalDate, List<Transaction>> entry : debitsByDate.entrySet()) {
      ChartPoint point = resolveMarkerPoint(entry.getKey(), pointByDate);
      if (point == null) {
        continue;
      }

      Long dayBalanceCents = balanceByDate.get(entry.getKey());
      if (dayBalanceCents == null) {
        continue;
      }
      String balanceLabel = resolveTooltipBalanceLabel(dayBalanceCents, locale);

      List<String> lines = entry.getValue().stream()
          .map(tx -> formatDebitTooltipLine(tx, locale, timeFormatter))
          .toList();
      int tooltipWidth = resolveTooltipWidth(lines, balanceLabel);
      int tooltipHeight = 50 + lines.size() * 16;
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
          balanceLabel,
          lines));
    }
    return markers;
  }

  private ChartPoint resolveMarkerPoint(LocalDate bookingDate, Map<LocalDate, ChartPoint> pointByDate) {
    return pointByDate.get(bookingDate);
  }

  private int resolveTooltipWidth(List<String> lines, String balanceLabel) {
    int longestLineLength = lines.stream().mapToInt(String::length).max().orElse(0);
    longestLineLength = Math.max(longestLineLength, balanceLabel.length());
    int estimated = 44 + (longestLineLength * 7);
    return Math.max(CHART_MIN_TOOLTIP_WIDTH, Math.min(CHART_MAX_TOOLTIP_WIDTH, estimated));
  }

  private String resolveTooltipBalanceLabel(long balanceCents, Locale locale) {
    return resolveBalanceLabelPrefix(locale) + ": " + formatAmount(balanceCents, locale);
  }

  private String resolveBalanceLabelPrefix(Locale locale) {
    if (Locale.GERMAN.getLanguage().equals(locale.getLanguage())) {
      return "Kontostand Tagesende";
    }
    return "End-of-day balance";
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
    if ("30d".equalsIgnoreCase(range)) {
      return 30;
    }
    throw new IllegalArgumentException("Unsupported chart range: " + range);
  }

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  private ChartScale resolveChartScale(long rawMin, long rawMax) {
    long span = Math.max(1L, rawMax - rawMin);
    long amplitude = Math.max(Math.abs(rawMin), Math.abs(rawMax));
    long padding = Math.max(1_000L, Math.max(span / 8L, amplitude / 12L));

    long expandedMin = rawMin - padding;
    long expandedMax = rawMax + padding;
    long tickStepCents = resolveTickStepCents(expandedMin, expandedMax);

    long axisMin = floorToStep(expandedMin, tickStepCents);
    long axisMax = ceilToStep(expandedMax, tickStepCents);

    if (axisMax - axisMin < tickStepCents * 2L) {
      axisMin -= tickStepCents;
      axisMax += tickStepCents;
    }

    return new ChartScale(axisMin, axisMax, tickStepCents);
  }

  private long resolveTickStepCents(long minCents, long maxCents) {
    double rangeEuros = Math.max(1.0d, (maxCents - minCents) / 100.0d);
    double roughStepEuros = rangeEuros / 4.0d;

    double magnitude = Math.pow(10.0d, Math.floor(Math.log10(roughStepEuros)));
    double normalized = roughStepEuros / magnitude;

    double niceNormalized;
    if (normalized <= 1.0d) {
      niceNormalized = 1.0d;
    } else if (normalized <= 2.0d) {
      niceNormalized = 2.0d;
    } else if (normalized <= 5.0d) {
      niceNormalized = 5.0d;
    } else {
      niceNormalized = 10.0d;
    }

    long stepEuros = (long) Math.ceil(niceNormalized * magnitude);
    return Math.max(100L, stepEuros * 100L);
  }

  private long floorToStep(long value, long step) {
    return Math.floorDiv(value, step) * step;
  }

  private long ceilToStep(long value, long step) {
    long floor = floorToStep(value, step);
    return floor == value ? value : floor + step;
  }

  private int toChartY(long valueCents, long minCents, long maxCents) {
    long range = Math.max(1L, maxCents - minCents);
    double y = CHART_PAD_TOP + ((maxCents - valueCents) / (double) range) * chartPlotHeight();
    return round(y);
  }

  private int resolveZeroY(long min, long max) {
    int plotTop = CHART_PAD_TOP;
    int plotBottom = CHART_HEIGHT - CHART_PAD_BOTTOM;

    if (max < 0) {
      return plotTop;
    }
    if (min >= 0) {
      return plotBottom;
    }

    int zeroY = toChartY(0L, min, max);
    return Math.max(plotTop, Math.min(plotBottom, zeroY));
  }

  private String formatAxisAmount(long cents, Locale locale) {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
    DecimalFormat format = new DecimalFormat("#,##0", symbols);
    format.setGroupingUsed(true);
    return format.format(cents / 100.0d) + " EUR";
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

  private record ChartScale(long minCents, long maxCents, long tickStepCents) {}

  private record ChartDebitMarker(
      int x,
      int y,
      int tooltipOffsetX,
      int tooltipOffsetY,
      int tooltipWidth,
      int tooltipHeight,
      String dayLabel,
      String balanceLabel,
      List<String> lines) {}
}
