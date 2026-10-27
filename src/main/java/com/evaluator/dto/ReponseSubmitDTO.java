package com.evaluator.dto;

import lombok.*;
import java.util.List;

// DTO pour soumettre les réponses de l'employé
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReponseSubmitDTO {
    private Long sessionId;
    private List<QuestionReponse> questionReponses;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionReponse {
        private String question;
        private String reponse;         // Réponse telle que rapportée par l'examinateur
        private Integer noteExaminateur; // Note subjective de l'examinateur (1-5), optionnel
    }
}
