package de.kruemelnerd.finanzapp.repository;

import de.kruemelnerd.finanzapp.domain.CsvArtifact;
import de.kruemelnerd.finanzapp.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CsvArtifactRepository extends JpaRepository<CsvArtifact, Integer> {
  List<CsvArtifact> findByUserAndDeletedAtIsNull(User user);

  Optional<CsvArtifact> findTopByUserAndDeletedAtIsNullOrderByUploadedAtDesc(User user);

  long countByUserAndDeletedAtIsNull(User user);

  long countByUserAndDeletedAtIsNotNull(User user);

  long countByUser(User user);

  @Modifying
  @Transactional
  @Query("update CsvArtifact c set c.deletedAt = :deletedAt where c.user = :user and c.deletedAt is null")
  int softDeleteByUser(User user, @Param("deletedAt") java.time.Instant deletedAt);

  @Modifying
  @Transactional
  @Query("delete from CsvArtifact c where c.user = :user")
  int deleteByUser(User user);
}
