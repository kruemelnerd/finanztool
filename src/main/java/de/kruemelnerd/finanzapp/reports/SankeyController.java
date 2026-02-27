package de.kruemelnerd.finanzapp.reports;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SankeyController {
  private final SankeyReportService sankeyReportService;

  public SankeyController(SankeyReportService sankeyReportService) {
    this.sankeyReportService = sankeyReportService;
  }

  @GetMapping("/reports/sankey")
  public String sankeyPage(
      @RequestParam(name = "year", required = false) Integer year,
      @AuthenticationPrincipal UserDetails userDetails,
      Model model) {
    java.util.List<Integer> years = sankeyReportService.loadAvailableYears(userDetails);
    int selectedYear = resolveSelectedYear(year, years);

    model.addAttribute("pageTitle", "page.sankey");
    model.addAttribute("yearOptions", years);
    model.addAttribute("selectedYear", selectedYear);
    return "reports-sankey";
  }

  @GetMapping("/api/reports/sankey")
  @ResponseBody
  public SankeyReportService.SankeyReportData sankeyData(
      @RequestParam(name = "year", required = false) Integer year,
      @AuthenticationPrincipal UserDetails userDetails) {
    int selectedYear = year == null ? sankeyReportService.defaultYear() : year;
    return sankeyReportService.buildReport(userDetails, selectedYear);
  }

  private int resolveSelectedYear(Integer requestedYear, java.util.List<Integer> years) {
    if (requestedYear != null && years.contains(requestedYear)) {
      return requestedYear;
    }
    if (!years.isEmpty()) {
      return years.get(0);
    }
    return sankeyReportService.defaultYear();
  }
}
