package com.evaluator.repository;

import com.evaluator.model.EvaluationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EvaluationSessionRepository extends JpaRepository<EvaluationSession, Long> {
    List<EvaluationSession> findAllByOrderByCreatedAtDesc();

    @Query("SELECT s FROM EvaluationSession s WHERE s.employee.id = :employeeId ORDER BY s.createdAt DESC")
    List<EvaluationSession> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
