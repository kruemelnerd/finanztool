package de.kruemelnerd.finanzapp.repository;

import de.kruemelnerd.finanzapp.domain.Category;
import de.kruemelnerd.finanzapp.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
  boolean existsByUserAndDeletedAtIsNull(User user);

  long countByUserAndDeletedAtIsNull(User user);

  Optional<Category> findByUserAndIsDefaultTrueAndDeletedAtIsNull(User user);

  Optional<Category> findByUserAndParentIsNullAndNameIgnoreCaseAndDeletedAtIsNull(User user, String name);

  Optional<Category> findByUserAndParentAndNameIgnoreCaseAndDeletedAtIsNull(User user, Category parent, String name);

  Optional<Category> findByIdAndUserAndDeletedAtIsNull(Integer id, User user);

  List<Category> findByUserAndDeletedAtIsNullAndParentIsNullOrderBySortOrderAscIdAsc(User user);

  List<Category> findByUserAndDeletedAtIsNullAndParentIsNotNullOrderBySortOrderAscIdAsc(User user);

  List<Category> findByUserAndParentAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User user, Category parent);
}
