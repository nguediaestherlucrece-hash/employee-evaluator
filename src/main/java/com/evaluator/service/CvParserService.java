package com.evaluator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service d'extraction du texte des CVs (PDF et DOCX)
 * Utilise Apache PDFBox et Apache POI — tous gratuits et open source
 */
@Service
@Slf4j
public class CvParserService {

    /**
     * Extrait le texte brut d'un CV uploadé (PDF ou DOCX)
     */
    public String extractText(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Nom de fichier invalide");
        }

        String extension = getExtension(originalFilename).toLowerCase();
        return switch (extension) {
            case "pdf"  -> extractFromPdf(file.getInputStream());
            case "docx" -> extractFromDocx(file.getInputStream());
            case "doc"  -> throw new IllegalArgumentException("Format .doc non supporté. Veuillez convertir en .docx");
            case "txt"  -> new String(file.getBytes());
            default     -> throw new IllegalArgumentException("Format non supporté: " + extension + ". Formats acceptés: PDF, DOCX, TXT");
        };
    }

    private String extractFromPdf(InputStream is) throws IOException {
        try (PDDocument doc = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            log.debug("PDF extrait: {} caractères", text.length());
            return text;
        }
    }

    private String extractFromDocx(InputStream is) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            log.debug("DOCX extrait: {} caractères", text.length());
            return text;
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex == -1 ? "" : filename.substring(dotIndex + 1);
    }

    /**
     * Valide que le fichier est bien un CV lisible et non vide
     */
    public void validateCvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier CV est obligatoire");
        }
        long maxSize = 10 * 1024 * 1024; // 10 MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Le fichier CV ne doit pas dépasser 10MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".pdf")
                && !filename.toLowerCase().endsWith(".docx")
                && !filename.toLowerCase().endsWith(".txt"))) {
            throw new IllegalArgumentException("Format accepté : PDF, DOCX, TXT");
        }
    }
}
