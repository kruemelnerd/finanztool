package com.example.finanzapp.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
@Controller
public class UploadExceptionHandler {
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public String handleMaxUploadSize(
      MaxUploadSizeExceededException ex,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    redirectAttributes.addFlashAttribute("csvImportStatus", "error");
    redirectAttributes.addFlashAttribute("csvImportMessage", "CSV exceeds 10MB limit");

    String path = request.getRequestURI();
    if (path != null && path.startsWith("/settings")) {
      return "redirect:/settings";
    }
    return "redirect:/overview";
  }
}
