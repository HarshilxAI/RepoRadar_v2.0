
package com.reporadar.controller;

import com.reporadar.dto.AiInsightsDto;
import com.reporadar.dto.ApiResponse;
import com.reporadar.dto.DashboardDto;
import com.reporadar.service.GemmaInsightsService;
import com.reporadar.service.RepositoryAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
            DashboardDto dashboard = analysisService.analyze(repositoryUrl);
            AiInsightsDto insights =
                    gemmaInsightsService.generateInsights(dashboard);

            return ApiResponse.success(
                    "AI repository insights generated successfully.",
                    insights);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to generate AI insights. Check the AI service configuration and availability.",
                    ex);
        }
    }
}
