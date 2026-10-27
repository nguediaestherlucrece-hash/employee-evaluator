package com.evaluator.controller;

import com.evaluator.dto.ReponseSubmitDTO;
import com.evaluator.model.EvaluationSession;
import com.evaluator.service.EvaluationService;
import com.evaluator.service.RapportPdfService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final RapportPdfService rapportPdfService;
    private final ObjectMapper objectMapper;

    // ===== DASHBOARD =====
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("sessions", evaluationService.getAllSessions());
        model.addAttribute("totalEmployes", evaluationService.getAllEmployees().size());
        long rapportsGeneres = evaluationService.getAllSessions().stream()
                .filter(s -> s.getStatus() == EvaluationSession.SessionStatus.RAPPORT_GENERE).count();
        model.addAttribute("rapportsGeneres", rapportsGeneres);
        return "dashboard";
    }

    // ===== ÉTAPE 1 : Upload CV =====
    @GetMapping("/evaluation/nouveau")
    public String nouveauFormulaire() {
        return "examiner/upload-cv";
    }

    @PostMapping("/evaluation/upload-cv")
    public String uploadCv(
            @RequestParam("cvFile") MultipartFile cvFile,
            @RequestParam("employeNom") String employeNom,
            @RequestParam("poste") String poste,
            @RequestParam(value = "departement", required = false) String departement,
            @RequestParam("examinateurName") String examinateurName,
            @RequestParam(defaultValue = "10") @Min(5) @Max(20) int nombreQuestions,
            RedirectAttributes redirectAttributes) {
        try {
            EvaluationSession session = evaluationService.creerSessionDepuisCv(
                    cvFile, employeNom, poste, departement, examinateurName, nombreQuestions);
            redirectAttributes.addFlashAttribute("successMsg", "Questions générées avec succès !");
            return "redirect:/evaluation/questions/" + session.getId();
        } catch (Exception e) {
            log.error("Erreur upload CV", e);
            redirectAttributes.addFlashAttribute("errorMsg", "Erreur: " + e.getMessage());
            return "redirect:/evaluation/nouveau";
        }
    }

    // ===== ÉTAPE 2 : Afficher les questions =====
    @GetMapping("/evaluation/questions/{sessionId}")
    public String afficherQuestions(@PathVariable Long sessionId, Model model) {
        EvaluationSession session = evaluationService.getSession(sessionId);
        model.addAttribute("session", session);

        // Parser le JSON des questions pour l'affichage
        try {
            String cleanJson = session.getQuestionsGenerees().trim()
                    .replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode questionsNode = objectMapper.readTree(cleanJson);
            model.addAttribute("questions", questionsNode.path("questions"));
        } catch (Exception e) {
            log.warn("Impossible de parser les questions JSON", e);
            model.addAttribute("questionsRaw", session.getQuestionsGenerees());
        }
        return "examiner/questions";
    }

    // ===== ÉTAPE 3 : Saisie des réponses =====
    @GetMapping("/evaluation/reponses/{sessionId}")
    public String formulaireReponses(@PathVariable Long sessionId, Model model) {
        EvaluationSession session = evaluationService.getSession(sessionId);
        model.addAttribute("session", session);

        try {
            String cleanJson = session.getQuestionsGenerees().trim()
                    .replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode questionsNode = objectMapper.readTree(cleanJson);
            model.addAttribute("questions", questionsNode.path("questions"));
        } catch (Exception e) {
            model.addAttribute("questionsRaw", session.getQuestionsGenerees());
        }
        return "examiner/saisie-reponses";
    }

    @PostMapping("/evaluation/soumettre-reponses")
    @ResponseBody
    public ResponseEntity<?> soumettreReponses(@RequestBody ReponseSubmitDTO dto) {
        try {
            EvaluationSession session = evaluationService.soumettreReponsesEtGenererRapport(dto);
            return ResponseEntity.ok().body(
                    java.util.Map.of(
                            "success", true,
                            "sessionId", session.getId(),
                            "redirectUrl", "/evaluation/rapport/" + session.getId()
                    )
            );
        } catch (Exception e) {
            log.error("Erreur soumission réponses", e);
            return ResponseEntity.internalServerError()
                    .body(java.util.Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ===== ÉTAPE 4 : Afficher le rapport =====
    @GetMapping("/evaluation/rapport/{sessionId}")
    public String afficherRapport(@PathVariable Long sessionId, Model model) {
        EvaluationSession session = evaluationService.getSession(sessionId);
        model.addAttribute("session", session);

        if (session.getRapportFinal() != null) {
            try {
                String cleanJson = session.getRapportFinal().trim()
                        .replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                JsonNode rapport = objectMapper.readTree(cleanJson);
                model.addAttribute("rapport", rapport);
            } catch (Exception e) {
                model.addAttribute("rapportRaw", session.getRapportFinal());
            }
        }
        return "report/rapport";
    }

    // ===== TÉLÉCHARGER LE PDF =====
    @GetMapping("/evaluation/rapport/{sessionId}/pdf")
    public ResponseEntity<byte[]> telechargerPdf(@PathVariable Long sessionId) {
        EvaluationSession session = evaluationService.getSession(sessionId);
        byte[] pdfBytes = rapportPdfService.genererRapportPdf(session);

        String filename = "rapport_" + session.getEmployee().getFullName()
                .replaceAll("\\s+", "_") + "_" + sessionId + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ===== LISTE DES SESSIONS =====
    @GetMapping("/sessions")
    public String listeSessions(Model model) {
        model.addAttribute("sessions", evaluationService.getAllSessions());
        return "examiner/sessions";
    }
}
