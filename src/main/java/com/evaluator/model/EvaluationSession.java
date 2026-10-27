package com.evaluator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "evaluation_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String examinateurName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    // Questions générées par l'IA à partir du CV
    @Column(columnDefinition = "TEXT")
    private String questionsGenerees;    // JSON stringifié

    // Réponses orales rapportées par l'examinateur
    @Column(columnDefinition = "TEXT")
    private String reponsesRapportees;   // JSON stringifié

    // Rapport final généré par GROQ
    @Column(columnDefinition = "TEXT")
    private String rapportFinal;

    // Score global estimé par l'IA (0-100)
    private Integer scoreGlobal;

    // Niveau estimé: DEBUTANT, INTERMEDIAIRE, AVANCE, EXPERT
    @Column
    private String niveauEstime;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.status = SessionStatus.EN_COURS;
    }

    public enum SessionStatus {
        EN_COURS,
        QUESTIONS_GENEREES,
        REPONSES_SOUMISES,
        RAPPORT_GENERE
    }
}
