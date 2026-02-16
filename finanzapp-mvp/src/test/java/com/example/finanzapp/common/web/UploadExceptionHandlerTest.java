package com.example.finanzapp.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class UploadExceptionHandlerTest {
  @Test
  void redirectsToOverviewWithFlashMessage() {
    UploadExceptionHandler handler = new UploadExceptionHandler();
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
    UploadExceptionHandler handler = new UploadExceptionHandler();
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
}
