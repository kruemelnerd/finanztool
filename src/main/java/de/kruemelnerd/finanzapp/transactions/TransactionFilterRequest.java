package de.kruemelnerd.finanzapp.transactions;

import java.math.BigDecimal;
import org.springframework.web.util.UriComponentsBuilder;

public class TransactionFilterRequest {
  private BigDecimal minAmount;
  private BigDecimal maxAmount;
  private String nameContains;
  private String purposeContains;
  private boolean onlyUncategorized;
  private Integer subcategoryId;
  private Integer parentCategoryId;
  private Integer page;

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public void setMinAmount(BigDecimal minAmount) {
    this.minAmount = minAmount;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(BigDecimal maxAmount) {
    this.maxAmount = maxAmount;
  }

  public String getNameContains() {
    return nameContains;
  }

  public void setNameContains(String nameContains) {
    this.nameContains = nameContains;
  }

  public String getPurposeContains() {
    return purposeContains;
  }

  public void setPurposeContains(String purposeContains) {
    this.purposeContains = purposeContains;
  }

  public boolean isOnlyUncategorized() {
    return onlyUncategorized;
  }

  public void setOnlyUncategorized(boolean onlyUncategorized) {
    this.onlyUncategorized = onlyUncategorized;
  }

  public Integer getSubcategoryId() {
    return subcategoryId;
  }

  public void setSubcategoryId(Integer subcategoryId) {
    this.subcategoryId = subcategoryId;
  }

  public Integer getParentCategoryId() {
    return parentCategoryId;
  }

  public void setParentCategoryId(Integer parentCategoryId) {
    this.parentCategoryId = parentCategoryId;
  }

  public Integer getPage() {
    return page;
  }

  public void setPage(Integer page) {
    this.page = page;
  }

  public UriComponentsBuilder applyQueryParams(UriComponentsBuilder builder) {
    return builder
        .queryParam("minAmount", minAmount)
        .queryParam("maxAmount", maxAmount)
        .queryParam("nameContains", nameContains)
        .queryParam("purposeContains", purposeContains)
        .queryParam("onlyUncategorized", onlyUncategorized)
        .queryParam("subcategoryId", subcategoryId)
        .queryParam("parentCategoryId", parentCategoryId)
        .queryParam("page", page);
  }
}
