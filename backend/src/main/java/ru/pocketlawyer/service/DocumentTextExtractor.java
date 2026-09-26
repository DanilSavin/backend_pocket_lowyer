package ru.pocketlawyer.service;

import ru.pocketlawyer.api.DocumentController.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class DocumentTextExtractor {
    public String extract(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(file.getBytes())) { text = new PDFTextStripper().getText(document); }
        } else if (name.endsWith(".docx")) {
            try (var document = new XWPFDocument(file.getInputStream()); var extractor = new XWPFWordExtractor(document)) { text = extractor.getText(); }
        } else if (name.endsWith(".txt")) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else {
            throw new ApiException("Поддерживаются только файлы PDF, DOCX и TXT.");
        }
        text = text == null ? "" : text.trim();
        if (text.isBlank()) throw new ApiException("Не удалось извлечь текст. Возможно, документ является сканом.");
        return text;
    }
}
