package com.example.finanzapp.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class UploadExceptionHandlerTest {
  @Test
  void redirectsToOverviewWithFlashMessage() {
    UploadExceptionHandler handler = newHandler();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
    HttpServletRequest request = new TestHttpServletRequest("/overview/import-csv");

    String view = handler.handleMaxUploadSize(
        new MaxUploadSizeExceededException(1L),
        request,
        redirectAttributes);

    assertThat(view).isEqualTo("redirect:/overview");
    assertThat(redirectAttributes.getFlashAttributes().get("csvImportStatus"))
        .isEqualTo("error");
    assertThat(redirectAttributes.getFlashAttributes().get("csvImportMessage"))
        .isEqualTo("CSV exceeds 10MB limit");
  }

  @Test
  void redirectsToSettingsWhenSettingsUpload() {
    UploadExceptionHandler handler = newHandler();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
    HttpServletRequest request = new TestHttpServletRequest("/settings/import-csv");

    String view = handler.handleMaxUploadSize(
        new MaxUploadSizeExceededException(1L),
        request,
        redirectAttributes);

    assertThat(view).isEqualTo("redirect:/settings");
  }

  private static class TestHttpServletRequest extends org.springframework.mock.web.MockHttpServletRequest {
    TestHttpServletRequest(String uri) {
      super();
      setRequestURI(uri);
    }
  }

  private UploadExceptionHandler newHandler() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("csv.import.error.maxSize", Locale.ENGLISH, "CSV exceeds 10MB limit");
    messageSource.addMessage("csv.import.error.maxSize", Locale.GERMAN, "CSV exceeds 10MB limit");
    messageSource.addMessage("csv.import.error.maxSize", Locale.GERMANY, "CSV exceeds 10MB limit");
    return new UploadExceptionHandler(messageSource);
  }
}
