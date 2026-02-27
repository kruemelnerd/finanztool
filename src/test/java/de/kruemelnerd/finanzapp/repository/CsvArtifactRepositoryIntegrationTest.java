package de.kruemelnerd.finanzapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvArtifactRepositoryIntegrationTest extends RepositoryIntegrationTestBase {
  @Test
  void findByUserAndDeletedAtIsNullReturnsActiveOnly() {
    User user = saveUser("user@example.com");
    User other = saveUser("other@example.com");

    CsvArtifact active = saveCsvArtifact(user, "active.csv", false);
    saveCsvArtifact(user, "deleted.csv", true);
    saveCsvArtifact(other, "other.csv", false);

    List<CsvArtifact> result = csvArtifactRepository.findByUserAndDeletedAtIsNull(user);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(active.getId());
  }
}
