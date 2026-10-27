package com.evaluator.service;

import com.evaluator.model.EvaluationSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Génère un rapport PDF formaté avec iText7 (AGPL - gratuit)
 */
@Service
@Slf4j
public class RapportPdfService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DeviceRgb BLEU_PRIMAIRE   = new DeviceRgb(0, 82, 155);
    private static final DeviceRgb VERT_SUCCES      = new DeviceRgb(34, 139, 34);
    private static final DeviceRgb ORANGE_ATTENTION = new DeviceRgb(255, 140, 0);
    private static final DeviceRgb ROUGE_ALERTE     = new DeviceRgb(200, 0, 0);
    private static final DeviceRgb GRIS_CLAIR       = new DeviceRgb(245, 245, 245);

    public byte[] genererRapportPdf(EvaluationSession session) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
             Document document = new Document(pdfDoc)) {

            PdfFont boldFont   = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // ---- EN-TÊTE ----
            Paragraph titre = new Paragraph("RAPPORT D'ÉVALUATION PROFESSIONNELLE")
                    .setFont(boldFont).setFontSize(18)
                    .setFontColor(BLEU_PRIMAIRE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5);
            document.add(titre);

            Paragraph sousTitre = new Paragraph("CONFIDENTIEL — Usage exclusif RH")
                    .setFont(regularFont).setFontSize(10)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(sousTitre);

            // ---- INFORMATIONS GÉNÉRALES ----
            document.add(sectionHeader("INFORMATIONS GÉNÉRALES", boldFont));
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{40, 60})).useAllAvailableWidth();
            addTableRow(infoTable, "Employé évalué", session.getEmployee().getFullName(), boldFont, regularFont);
            addTableRow(infoTable, "Poste", session.getEmployee().getPoste(), boldFont, regularFont);
            addTableRow(infoTable, "Département", session.getEmployee().getDepartement(), boldFont, regularFont);
            addTableRow(infoTable, "Examinateur", session.getExaminateurName(), boldFont, regularFont);
            if (session.getCompletedAt() != null) {
                addTableRow(infoTable, "Date d'évaluation",
                        session.getCompletedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        boldFont, regularFont);
            }
            document.add(infoTable);
            document.add(new Paragraph("\n"));

            // ---- SCORE & NIVEAU ----
            if (session.getScoreGlobal() != null) {
                document.add(sectionHeader("RÉSULTAT GLOBAL", boldFont));
                Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
                Cell scoreCell = new Cell()
                        .add(new Paragraph("Score Global: " + session.getScoreGlobal() + "/100")
                                .setFont(boldFont).setFontSize(20)
                                .setFontColor(scoreColor(session.getScoreGlobal())))
                        .setBackgroundColor(GRIS_CLAIR).setPadding(10);
                Cell niveauCell = new Cell()
                        .add(new Paragraph("Niveau: " + session.getNiveauEstime())
                                .setFont(boldFont).setFontSize(16)
                                .setFontColor(BLEU_PRIMAIRE))
                        .setBackgroundColor(GRIS_CLAIR).setPadding(10);
                scoreTable.addCell(scoreCell);
                scoreTable.addCell(niveauCell);
                document.add(scoreTable);
                document.add(new Paragraph("\n"));
            }

            // ---- CONTENU DU RAPPORT JSON ----
            if (session.getRapportFinal() != null) {
                try {
                    String cleanJson = session.getRapportFinal().trim()
                            .replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                    JsonNode rapport = objectMapper.readTree(cleanJson);
                    appendRapportContent(document, rapport, boldFont, regularFont);
                } catch (Exception e) {
                    log.warn("Impossible de parser le rapport JSON, affichage texte brut", e);
                    document.add(new Paragraph(session.getRapportFinal()).setFont(regularFont));
                }
            }

            // ---- PIED DE PAGE ----
            document.add(new Paragraph("\n\n"));
            document.add(new Paragraph("Ce rapport est généré automatiquement par le système d'évaluation IA. "
                    + "Il doit être traité avec la plus grande confidentialité.")
                    .setFont(regularFont).setFontSize(8)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

        } catch (Exception e) {
            log.error("Erreur génération PDF", e);
            throw new RuntimeException("Impossible de générer le PDF: " + e.getMessage());
        }
        return baos.toByteArray();
    }

    private void appendRapportContent(Document doc, JsonNode rapport, PdfFont bold, PdfFont regular) {
        // Résumé exécutif
        if (rapport.has("resume_executif")) {
            doc.add(sectionHeader("RÉSUMÉ EXÉCUTIF", bold));
            doc.add(new Paragraph(rapport.path("resume_executif").asText()).setFont(regular).setMarginBottom(10));
        }

        // Forces
        if (rapport.has("forces")) {
            doc.add(sectionHeader("POINTS FORTS", bold));
            for (JsonNode force : rapport.path("forces")) {
                String score = force.has("score") ? " (Score: " + force.path("score").asInt() + "/10)" : "";
                doc.add(new Paragraph("✓ " + force.path("competence").asText() + score)
                        .setFont(bold).setFontColor(VERT_SUCCES));
                doc.add(new Paragraph("  " + force.path("details").asText()).setFont(regular).setMarginBottom(5));
            }
        }

        // Axes d'amélioration
        if (rapport.has("axes_amelioration")) {
            doc.add(sectionHeader("AXES D'AMÉLIORATION", bold));
            for (JsonNode axe : rapport.path("axes_amelioration")) {
                doc.add(new Paragraph("⚠ " + axe.path("competence").asText())
                        .setFont(bold).setFontColor(ORANGE_ATTENTION));
                doc.add(new Paragraph("  " + axe.path("details").asText()).setFont(regular));
                if (axe.has("recommandation")) {
                    doc.add(new Paragraph("  → " + axe.path("recommandation").asText())
                            .setFont(regular).setFontColor(BLEU_PRIMAIRE).setMarginBottom(5));
                }
            }
        }

        // Cohérence CV
        if (rapport.has("coherence_cv_realite")) {
            JsonNode coherence = rapport.path("coherence_cv_realite");
            doc.add(sectionHeader("COHÉRENCE CV vs RÉALITÉ", bold));
            doc.add(new Paragraph("Taux de cohérence: " + coherence.path("taux_coherence").asInt() + "%")
                    .setFont(bold));
            doc.add(new Paragraph(coherence.path("observations").asText()).setFont(regular).setMarginBottom(10));
        }

        // Recommandation finale
        if (rapport.has("recommandation_finale")) {
            doc.add(sectionHeader("RECOMMANDATION FINALE", bold));
            doc.add(new Paragraph(rapport.path("recommandation_finale").asText())
                    .setFont(bold).setFontSize(14).setFontColor(BLEU_PRIMAIRE));
            if (rapport.has("details_recommandation")) {
                doc.add(new Paragraph(rapport.path("details_recommandation").asText()).setFont(regular));
            }
        }
    }

    private Paragraph sectionHeader(String text, PdfFont font) {
        return new Paragraph(text)
                .setFont(font).setFontSize(12)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(BLEU_PRIMAIRE)
                .setPadding(5).setMarginTop(10).setMarginBottom(5);
    }

    private void addTableRow(Table table, String label, String value, PdfFont bold, PdfFont regular) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(bold).setFontSize(10)));
        table.addCell(new Cell().add(new Paragraph(value != null ? value : "-").setFont(regular).setFontSize(10)));
    }

    private DeviceRgb scoreColor(int score) {
        if (score >= 75) return VERT_SUCCES;
        if (score >= 50) return ORANGE_ATTENTION;
        return ROUGE_ALERTE;
    }
}
