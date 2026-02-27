package de.kruemelnerd.finanzapp.transactions;

import java.util.List;

public record TransactionPage(
    List<TransactionRow> rows,
    int page,
    int pageSize,
    int totalPages,
    long totalItems,
    Integer categoryIdFilter,
    Integer parentCategoryIdFilter,
    String categoryFilterLabel) {
  public TransactionPage(
      List<TransactionRow> rows,
      int page,
      int pageSize,
      int totalPages,
      long totalItems) {
    this(rows, page, pageSize, totalPages, totalItems, null, null, null);
  }

  public boolean hasPreviousPage() {
    return page > 0;
  }

  public boolean hasNextPage() {
    return page + 1 < totalPages;
  }

  public int previousPage() {
    return hasPreviousPage() ? page - 1 : 0;
  }

  public int nextPage() {
    return hasNextPage() ? page + 1 : Math.max(totalPages - 1, 0);
  }
}
