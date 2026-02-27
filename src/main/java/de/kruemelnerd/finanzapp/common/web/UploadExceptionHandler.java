package de.kruemelnerd.finanzapp.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
@Controller
public class UploadExceptionHandler {
  private final MessageSource messageSource;

  public UploadExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public String handleMaxUploadSize(
      MaxUploadSizeExceededException ex,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    redirectAttributes.addFlashAttribute("csvImportStatus", "error");
    redirectAttributes.addFlashAttribute("csvImportMessage", msg("csv.import.error.maxSize"));

    String path = request.getRequestURI();
    if (path != null && path.startsWith("/settings")) {
      return "redirect:/settings";
    }
    return "redirect:/overview";
  }

  private String msg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
