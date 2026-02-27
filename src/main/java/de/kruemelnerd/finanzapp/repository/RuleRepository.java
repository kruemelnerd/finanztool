package de.kruemelnerd.finanzapp.repository;

import de.kruemelnerd.finanzapp.domain.Rule;
import de.kruemelnerd.finanzapp.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRepository extends JpaRepository<Rule, Integer> {
  List<Rule> findByUserAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User user);

  List<Rule> findByUserAndIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User user);

  Optional<Rule> findByIdAndUserAndDeletedAtIsNull(Integer id, User user);

  List<Rule> findByUserAndIdInAndDeletedAtIsNull(User user, List<Integer> ids);

  List<Rule> findByUserAndCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User user, Integer categoryId);

  long countByUserAndCategoryIdAndDeletedAtIsNull(User user, Integer categoryId);

  List<Rule> findByUserAndCategoryIdAndIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User user, Integer categoryId);
}
