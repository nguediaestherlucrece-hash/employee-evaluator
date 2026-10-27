package com.evaluator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column
    private String poste;

    @Column
    private String departement;

    @Column
    private String email;

    @Column
    private String cvFileName;

    @Column(columnDefinition = "TEXT")
    private String cvTextContent;   // Texte extrait du CV

    @Column
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EvaluationSession> sessions;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
