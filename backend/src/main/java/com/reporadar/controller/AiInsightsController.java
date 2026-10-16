
package com.reporadar.controller;

import com.reporadar.dto.AiInsightsDto;
import com.reporadar.dto.ApiResponse;
import com.reporadar.dto.DashboardDto;
import com.reporadar.service.GemmaInsightsService;
import com.reporadar.service.RepositoryAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AiInsightsController {

    private final RepositoryAnalysisService analysisService;
    private final GemmaInsightsService gemmaInsightsService;

    public AiInsightsController(
            RepositoryAnalysisService analysisService,
            GemmaInsightsService gemmaInsightsService) {
        this.analysisService = analysisService;
        this.gemmaInsightsService = gemmaInsightsService;
    }

    @PostMapping("/ai-insights")
    public ApiResponse<AiInsightsDto> generateInsights(
            @RequestParam String repositoryUrl) {
        try {
            DashboardDto dashboard = analysisService.analyzeForAi(repositoryUrl);
            AiInsightsDto insights =
                    gemmaInsightsService.generateInsights(dashboard);

            return ApiResponse.success(
                    "AI repository insights generated successfully.",
                    insights);
        } catch (IllegalArgumentException ex) {
            throw new com.reporadar.exception.ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new com.reporadar.exception.ApiException(
                    HttpStatus.BAD_GATEWAY, "Unable to generate AI insights. Please try again shortly.");
        }
    }
}
