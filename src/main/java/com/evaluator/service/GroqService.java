package com.evaluator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.model}")
    private String groqModel;

    @Value("${groq.max-tokens}")
    private int maxTokens;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Génère des questions d'entretien à partir du contenu du CV.
     * Les questions doivent être naturelles, conversationnelles,
     * pour ne pas éveiller les soupçons de l'employé évalué.
     */
    public String genererQuestions(String cvContent, String poste, int nombreQuestions) {
        String systemPrompt = """
                Tu es un expert RH chargé de préparer une évaluation discrète d'un employé.
                À partir du CV fourni, génère exactement %d questions d'entretien.
                Ces questions doivent :
                - Sembler être de simples questions de conversation professionnelle
                - Permettre d'évaluer les compétences réelles sans que l'employé sache qu'il est testé
                - Couvrir: compétences techniques, expériences passées, résolution de problèmes, soft skills
                - Être formulées naturellement (ex: "Comment tu abordes généralement..." plutôt que "Êtes-vous capable de...")
                
                Réponds UNIQUEMENT au format JSON suivant, sans aucun texte supplémentaire:
                {
                  "questions": [
                    {
                      "id": 1,
                      "categorie": "technique|comportementale|situationnelle|experience",
                      "question": "texte de la question",
                      "objectif": "ce qu'on cherche à évaluer",
                      "mots_cles_attendus": ["mot1", "mot2"]
                    }
                  ]
                }
                """.formatted(nombreQuestions);

        String userPrompt = "Poste: %s\n\nContenu du CV:\n%s".formatted(poste, cvContent);
        return callGroqApi(systemPrompt, userPrompt);
    }

    /**
     * Analyse les réponses de l'employé et génère un rapport d'évaluation complet.
     */
    public String analyserReponsesEtGenererRapport(String cvContent, String questionsJson,
                                                    String reponsesJson, String poste) {
        String systemPrompt = """
                Tu es un expert en évaluation des compétences professionnelles.
                Analyse les réponses d'un employé à des questions d'entretien et génère un rapport d'évaluation détaillé.
                
                Réponds UNIQUEMENT au format JSON suivant, sans texte supplémentaire:
                {
                  "score_global": <0-100>,
                  "niveau_estime": "<DEBUTANT|INTERMEDIAIRE|AVANCE|EXPERT>",
                  "resume_executif": "<2-3 phrases de synthèse>",
                  "forces": [
                    {"competence": "...", "details": "...", "score": <0-10>}
                  ],
                  "axes_amelioration": [
                    {"competence": "...", "details": "...", "recommandation": "..."}
                  ],
                  "analyse_par_categorie": {
                    "competences_techniques": {"score": <0-100>, "commentaire": "..."},
                    "experience": {"score": <0-100>, "commentaire": "..."},
                    "soft_skills": {"score": <0-100>, "commentaire": "..."},
                    "resolution_problemes": {"score": <0-100>, "commentaire": "..."}
                  },
                  "coherence_cv_realite": {
                    "taux_coherence": <0-100>,
                    "observations": "..."
                  },
                  "recommandation_finale": "<CONFIRMER_POSTE|FORMATION_RECOMMANDEE|REORIENTATION_CONSEILLE|PROMOTION_POSSIBLE>",
                  "details_recommandation": "...",
                  "plan_developpement": [
                    {"action": "...", "priorite": "HAUTE|MOYENNE|BASSE", "delai": "..."}
                  ]
                }
                """;

        String userPrompt = """
                Poste évalué: %s
                
                CV de l'employé:
                %s
                
                Questions posées et réponses de l'employé:
                %s
                """.formatted(poste, cvContent, combineQuestionsReponses(questionsJson, reponsesJson));

        return callGroqApi(systemPrompt, userPrompt);
    }

    /**
     * Appel générique à l'API GROQ
     */
    private String callGroqApi(String systemPrompt, String userPrompt) {
        try {
            OkHttpClient client = buildClient();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", groqModel);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", 0.3);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            requestBody.set("messages", messages);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(groqApiUrl)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No body";
                    log.error("GROQ API error: {} - {}", response.code(), errorBody);
                    throw new RuntimeException("Erreur API GROQ: " + response.code());
                }

                String responseBody = response.body().string();
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return jsonResponse
                        .path("choices")
                        .get(0)
                        .path("message")
                        .path("content")
                        .asText();
            }

        } catch (IOException e) {
            log.error("Erreur lors de l'appel GROQ API", e);
            throw new RuntimeException("Impossible de contacter l'API GROQ: " + e.getMessage());
        }
    }

    private String combineQuestionsReponses(String questionsJson, String reponsesJson) {
        try {
            JsonNode questions = objectMapper.readTree(questionsJson);
            JsonNode reponses = objectMapper.readTree(reponsesJson);
            StringBuilder sb = new StringBuilder();

            if (reponses.isArray()) {
                for (JsonNode rep : reponses) {
                    sb.append("Q: ").append(rep.path("question").asText()).append("\n");
                    sb.append("R: ").append(rep.path("reponse").asText()).append("\n");
                    if (rep.has("noteExaminateur")) {
                        sb.append("Note examinateur: ").append(rep.path("noteExaminateur").asInt()).append("/5\n");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Questions: " + questionsJson + "\nRéponses: " + reponsesJson;
        }
    }
}
