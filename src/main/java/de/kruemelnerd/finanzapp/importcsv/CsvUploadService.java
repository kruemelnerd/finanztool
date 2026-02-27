package de.kruemelnerd.finanzapp.importcsv;

import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.repository.UserRepository;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvUploadService {
  private static final Logger log = LoggerFactory.getLogger(CsvUploadService.class);

  private final CsvImportService csvImportService;
  private final UserRepository userRepository;

  public CsvUploadService(CsvImportService csvImportService, UserRepository userRepository) {
    this.csvImportService = csvImportService;
    this.userRepository = userRepository;
  }

  public CsvImportResult importForEmail(String email, MultipartFile file) {
    String fileName = file == null ? "<null>" : file.getOriginalFilename();
    String contentType = file == null ? "<null>" : file.getContentType();
    long sizeBytes = file == null ? -1L : file.getSize();

    if (file == null || file.isEmpty()) {
      throw new CsvImportException("CSV file is empty");
    }
    if (email == null || email.isBlank()) {
      throw new CsvImportException("User not found");
    }
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new CsvImportException("User not found"));
    try {
      return csvImportService.importCsv(
          user,
          fileName,
          contentType,
          file.getBytes());
    } catch (CsvImportException ex) {
      log.warn(
          "CSV import failed for user='{}', file='{}', contentType='{}', sizeBytes={}: {}",
          email,
          fileName,
          contentType,
          sizeBytes,
          ex.getMessage(),
          ex);
      throw ex;
    } catch (IOException ex) {
      log.error(
          "CSV upload I/O failed for user='{}', file='{}', contentType='{}', sizeBytes={}",
          email,
          fileName,
          contentType,
          sizeBytes,
          ex);
      throw new CsvImportException("CSV upload failed", ex);
    }
  }
}
