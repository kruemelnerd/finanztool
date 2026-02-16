package com.example.finanzapp.importcsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.finanzapp.domain.User;
import com.example.finanzapp.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class CsvUploadServiceTest {
  @Mock
  private CsvImportService csvImportService;

  @Mock
  private UserRepository userRepository;

  private CsvUploadService csvUploadService;

  @BeforeEach
  void setUp() {
    csvUploadService = new CsvUploadService(csvImportService, userRepository);
  }

  @Test
  void importForEmailRejectsEmptyFile() {
    MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

    assertThatThrownBy(() -> csvUploadService.importForEmail("user@example.com", file))
        .isInstanceOf(CsvImportException.class)
        .hasMessage("CSV file is empty");

    verifyNoInteractions(userRepository);
    verifyNoInteractions(csvImportService);
  }

  @Test
  void importForEmailRejectsUnknownUser() {
    MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "data".getBytes());

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> csvUploadService.importForEmail("user@example.com", file))
        .isInstanceOf(CsvImportException.class)
        .hasMessage("User not found");

    verify(userRepository).findByEmail("user@example.com");
    verifyNoInteractions(csvImportService);
  }

  @Test
  void importForEmailDelegatesToImportService() {
    MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "data".getBytes());
    User user = new User();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed");

    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(csvImportService.importCsv(eq(user), eq("data.csv"), eq("text/csv"), any(byte[].class)))
        .thenReturn(new CsvImportResult(3, 0, java.util.List.of()));

    CsvImportResult result = csvUploadService.importForEmail("user@example.com", file);

    assertThat(result.importedCount()).isEqualTo(3);
    verify(csvImportService)
        .importCsv(eq(user), eq("data.csv"), eq("text/csv"), any(byte[].class));
  }
}
