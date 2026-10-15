
package com.reporadar.dto;

import java.util.List;

public record AiInsightsDto(
        String repositoryName,
        String overallAssessment,
        List<Insight> insights,
        List<String> recommendedNextSteps,
        String limitations
) {
    public record Insight(
            String title,
            String priority,
            String explanation,
            List<String> evidence,
            String recommendation
    ) {
    }
}
