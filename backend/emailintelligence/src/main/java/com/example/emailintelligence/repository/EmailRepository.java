package com.example.emailintelligence.repository;

import com.example.emailintelligence.model.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {

    List<Email> findByUserId(Long userId);

    List<Email> findByUserIdAndPriority(Long userId, String priority);

    long countByUserId(Long userId);

    long countByUserIdAndCategory(Long userId, String category);

    long countByUserIdAndPriority(Long userId, String priority);

    boolean existsByMessageIdAndUserId(String messageId, Long userId);

    Email findByMessageIdAndUserId(String messageId, Long userId); // ← NEW

    List<Email> findByUserIdOrderByDateDesc(Long userId);

    List<Email> findByUserIdAndPriorityOrderByDateDesc(Long userId, String priority);

    long countByUserIdAndIsReadFalseAndDateAfter(Long userId, LocalDateTime date);

    List<Email> findByUserIdAndDateAfterOrderByDateDesc(Long userId, LocalDateTime date);

    List<Email> findByUserIdAndCategoryAndDateAfterOrderByDateDesc(
            Long userId,
            String category,
            LocalDateTime date
    );

    @Query("SELECT MAX(e.date) FROM Email e WHERE e.userId = :uid")
    LocalDateTime getLastSavedDate(@Param("uid") Long uid);
}