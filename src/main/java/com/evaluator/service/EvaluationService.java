package com.evaluator.service;

import com.evaluator.dto.ReponseSubmitDTO;
import com.evaluator.model.Employee;
import com.evaluator.model.EvaluationSession;
import com.evaluator.repository.EmployeeRepository;
import com.evaluator.repository.EvaluationSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EvaluationService {

    private final EmployeeRepository employeeRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final CvParserService cvParserService;
    private final GroqService groqService;
    private final RapportPdfService rapportPdfService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.dir}")
    private String uploadDir;

    // -------------------------------------------------------
    // ÉTAPE 1 : Uploader le CV et créer l'employé + session
    // -------------------------------------------------------
    public EvaluationSession creerSessionDepuisCv(MultipartFile cvFile,
                                                   String employeNom,
                                                   String poste,
                                                   String departement,
                                                   String examinateurName,
                                                   int nombreQuestions) throws IOException {
        // Valider le fichier
        cvParserService.validateCvFile(cvFile);

        // Sauvegarder le fichier sur disque
        String savedFilename = saveFile(cvFile);

        // Extraire le texte du CV
        String cvText = cvParserService.extractText(cvFile);
        log.info("CV extrait pour {}: {} caractères", employeNom, cvText.length());

        // Créer ou trouver l'employé
        Employee employee = Employee.builder()
                .fullName(employeNom)
                .poste(poste)
                .departement(departement)
                .cvFileName(savedFilename)
                .cvTextContent(cvText)
                .build();
        employee = employeeRepository.save(employee);

        // Générer les questions via GROQ
        String questionsJson = groqService.genererQuestions(cvText, poste, nombreQuestions);
        log.info("Questions générées pour {}", employeNom);

        // Créer la session
        EvaluationSession session = EvaluationSession.builder()
                .employee(employee)
                .examinateurName(examinateurName)
                .questionsGenerees(questionsJson)
                .status(EvaluationSession.SessionStatus.QUESTIONS_GENEREES)
                .build();
        return sessionRepository.save(session);
    }

    // -------------------------------------------------------
    // ÉTAPE 2 : Soumettre les réponses et générer le rapport
    // -------------------------------------------------------
    public EvaluationSession soumettreReponsesEtGenererRapport(ReponseSubmitDTO dto) throws Exception {
        EvaluationSession session = sessionRepository.findById(dto.getSessionId())
                .orElseThrow(() -> new RuntimeException("Session introuvable: " + dto.getSessionId()));

        // Sérialiser les réponses
        String reponsesJson = objectMapper.writeValueAsString(dto.getQuestionReponses());
        session.setReponsesRapportees(reponsesJson);
        session.setStatus(EvaluationSession.SessionStatus.REPONSES_SOUMISES);

        Employee employee = session.getEmployee();

        // Analyser avec GROQ et générer le rapport
        String rapportJson = groqService.analyserReponsesEtGenererRapport(
                employee.getCvTextContent(),
                session.getQuestionsGenerees(),
                reponsesJson,
                employee.getPoste()
        );

        // Parser le JSON du rapport pour extraire score et niveau
        try {
            String cleanJson = rapportJson.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            JsonNode rapportNode = objectMapper.readTree(cleanJson);
            session.setScoreGlobal(rapportNode.path("score_global").asInt(0));
            session.setNiveauEstime(rapportNode.path("niveau_estime").asText("N/A"));
        } catch (Exception e) {
            log.warn("Impossible de parser le score depuis le rapport JSON", e);
        }

        session.setRapportFinal(rapportJson);
        session.setStatus(EvaluationSession.SessionStatus.RAPPORT_GENERE);
        session.setCompletedAt(LocalDateTime.now());

        return sessionRepository.save(session);
    }

    // -------------------------------------------------------
    // UTILITAIRES
    // -------------------------------------------------------
    public List<EvaluationSession> getAllSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }

    public EvaluationSession getSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session introuvable: " + id));
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    private String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String uniqueFilename = UUID.randomUUID() + "." + extension;
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return uniqueFilename;
    }
}
