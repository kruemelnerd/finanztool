package com.example.finanzapp.importcsv;

import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.UserRepository;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvUploadService {
  private final CsvImportService csvImportService;
  private final UserRepository userRepository;

  public CsvUploadService(CsvImportService csvImportService, UserRepository userRepository) {
    this.csvImportService = csvImportService;
    this.userRepository = userRepository;
  }

  public CsvImportResult importForEmail(String email, MultipartFile file) {
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
          file.getOriginalFilename(),
          file.getContentType(),
          file.getBytes());
    } catch (IOException ex) {
      throw new CsvImportException("CSV upload failed", ex);
    }
  }
}
