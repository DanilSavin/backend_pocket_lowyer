package ru.pocketlawyer.api;

import java.util.List;

public record AnalysisResponse(
        String fileName,
        long fileSizeBytes,
        String summary,
        List<String> strengths,
        String riskLevel,
        List<Finding> findings,
        String disclaimer
) {
    public record Finding(String title, String severity, String description, String recommendation) {}
}
