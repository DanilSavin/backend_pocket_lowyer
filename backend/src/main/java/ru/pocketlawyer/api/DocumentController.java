package ru.pocketlawyer.api;

import ru.pocketlawyer.service.LegalAnalysisService;
import ru.pocketlawyer.service.GigaChatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentController {
    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private final LegalAnalysisService analysisService;

    public DocumentController(LegalAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ApiException("Выберите непустой файл.");
        log.info("Получен документ для анализа: name={}, sizeBytes={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        return analysisService.analyze(file);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ApiException.class)
    public ErrorResponse handle(ApiException e) { return new ErrorResponse(e.getMessage()); }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(GigaChatException.class)
    public ErrorResponse handle(GigaChatException e) {
        log.warn("Ошибка интеграции GigaChat: {}", e.getMessage());
        return new ErrorResponse(e.getMessage());
    }

    public record ErrorResponse(String message) {}
    public static class ApiException extends RuntimeException { public ApiException(String message) { super(message); } }
}
