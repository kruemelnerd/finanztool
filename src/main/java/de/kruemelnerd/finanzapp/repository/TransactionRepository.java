package de.kruemelnerd.finanzapp.repository;

import de.kruemelnerd.finanzapp.domain.Transaction;
import de.kruemelnerd.finanzapp.domain.User;
import de.kruemelnerd.finanzapp.domain.Category;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
  List<Transaction> findByUserAndDeletedAtIsNullOrderByBookingDateTimeDesc(User user);

  List<Transaction> findByUserAndDeletedAtIsNullOrderByBookingDateTimeAsc(User user);

  Optional<Transaction> findByIdAndUserAndDeletedAtIsNull(Integer id, User user);

  long countByUserAndDeletedAtIsNull(User user);

  long countByUserAndCategoryAndDeletedAtIsNull(User user, Category category);

  long countByUserAndDeletedAtIsNotNull(User user);

  long countByUser(User user);

  @Modifying
  @Transactional
  @Query("update Transaction t set t.deletedAt = :deletedAt where t.user = :user and t.deletedAt is null")
  int softDeleteByUser(User user, @Param("deletedAt") java.time.Instant deletedAt);

  @Modifying
  @Transactional
  @Query("delete from Transaction t where t.user = :user")
  int deleteByUser(User user);

  @Query("select t from Transaction t where t.user = :user and t.deletedAt is null and t.bookingDateTime <= :cutoff")
  List<Transaction> findActiveUpTo(User user, @Param("cutoff") LocalDateTime cutoff);

  @Query("""
      select t from Transaction t
      where t.user = :user
        and t.deletedAt is null
        and t.bookingDateTime >= :start
        and t.bookingDateTime < :end
      order by t.bookingDateTime asc
      """)
  List<Transaction> findDebitsInRange(
      @Param("user") User user,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Modifying
  @Transactional
  @Query("update Transaction t set t.deletedAt = :deletedAt where t.id = :id and t.user = :user and t.deletedAt is null")
  int softDeleteByIdAndUser(
      @Param("id") Integer id,
      @Param("user") User user,
      @Param("deletedAt") java.time.Instant deletedAt);
}
