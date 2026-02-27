package de.kruemelnerd.finanzapp.importcsv;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Service
public class CsvImportFlashService {
  private final CsvUploadService csvUploadService;
  private final MessageSource messageSource;

  public CsvImportFlashService(CsvUploadService csvUploadService, MessageSource messageSource) {
    this.csvUploadService = csvUploadService;
    this.messageSource = messageSource;
  }

  public void importWithFlash(
      MultipartFile file,
      UserDetails userDetails,
      RedirectAttributes redirectAttributes) {
    String email = userDetails == null ? null : userDetails.getUsername();
    try {
      CsvImportResult result = csvUploadService.importForEmail(email, file);
      redirectAttributes.addFlashAttribute("csvImportStatus", "success");
      redirectAttributes.addFlashAttribute(
          "csvImportMessage",
          messageSource.getMessage(
              "csv.import.success",
              new Object[] {result.importedCount()},
              LocaleContextHolder.getLocale()));
      addDuplicateNotice(result, redirectAttributes);
    } catch (CsvImportException ex) {
      redirectAttributes.addFlashAttribute("csvImportStatus", "error");
      redirectAttributes.addFlashAttribute("csvImportMessage", ex.getMessage());
    }
  }

  private void addDuplicateNotice(CsvImportResult result, RedirectAttributes redirectAttributes) {
    if (result.duplicateCount() <= 0) {
      return;
    }
    redirectAttributes.addFlashAttribute("csvImportDuplicates", result.duplicateSamples());
    redirectAttributes.addFlashAttribute("csvImportDuplicateCount", result.duplicateCount());
  }
}
