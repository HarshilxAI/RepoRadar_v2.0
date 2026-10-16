package com.reporadar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.reporadar.dto.AiInsightsDto;
import com.reporadar.dto.DashboardDto;
import com.reporadar.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Instant;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemmaInsightsServiceTest {
    @Test
    void failsBeforeCallingProviderWhenApiKeyIsMissing() {
        GemmaInsightsService service = new GemmaInsightsService(
                new ObjectMapper(), "", "test-model", "http://127.0.0.1:1", 1, 1, 1200);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.generateInsights(dashboard()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals("AI insights are not configured on this server. Please try again later.", exception.getMessage());
    }

    @Test
    void parsesValidJsonOutput() throws Exception {
        AiInsightsDto result = service().parseOutput("owner/repository", """
                {"overallAssessment":"Good documentation.","insights":[{"title":"Improve tests","priority":"Medium","explanation":"No tests were detected.","evidence":["Tests is missing"],"recommendation":"Add focused tests."}],"recommendedNextSteps":["Add tests."],"limitations":"Metadata only."}
                """);

        assertEquals("owner/repository", result.repositoryName());
        assertEquals(1, result.insights().size());
    }

    @Test
    void parsesJsonWrappedInAMarkdownFence() throws Exception {
        AiInsightsDto result = service().parseOutput("owner/repository", """
                ```json
                {"overallAssessment":"Useful README.","insights":[],"recommendedNextSteps":[],"limitations":"Metadata only."}
                ```
                """);

        assertEquals("Useful README.", result.overallAssessment());
    }

    @Test
    void parsesFunctionArgumentsUsingTheExistingDtoContract() throws Exception {
        AiInsightsDto result = service().parseOutput("owner/repository", """
                {"overallAssessment":"Structured output.","insights":[{"title":"Add CI","priority":"High","explanation":"No workflow was detected.","evidence":["CI workflow is missing"],"recommendation":"Add a workflow."}],"recommendedNextSteps":["Add CI."],"limitations":"Metadata only."}
                """);

        assertEquals("Add CI", result.insights().getFirst().title());
        assertEquals("High", result.insights().getFirst().priority());
    }

    @Test
    void rejectsMalformedJsonOutput() {
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> service().parseOutput("owner/repository", "{\"overallAssessment\":\"broken\" }}"));
    }

    @Test
    void rejectsIncompleteJsonOutput() {
        assertThrows(RuntimeException.class,
                () -> service().parseOutput("owner/repository", "{\"overallAssessment\":\"Incomplete\",\"insights\":[],\"recommendedNextSteps\":[]}"));
    }

    @Test
    void terminatesTheUnderlyingProviderRequestAtTheConfiguredDeadline() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = responseServer("{}", 3_000, requests);
        try {
            GemmaInsightsService service = new GemmaInsightsService(new ObjectMapper(), "test-key", "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1, 1, 1200);

            long started = System.nanoTime();
            ApiException exception = assertThrows(ApiException.class, () -> service.generateInsights(completeDashboard()));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

            assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.status());
            assertEquals(temporaryProviderMessage(), exception.getMessage());
            assertEquals(1, requests.get());
            assertTrue(elapsedMillis < 2_000, "request should terminate near its one-second deadline, not wait for the response");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsAnEmptyProviderResponseWithoutCorrectiveRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = responseServer("""
                {"choices":[{"finish_reason":"stop","message":{"content":"","tool_calls":[]}}]}
                """, 0, requests);
        try {
            GemmaInsightsService service = new GemmaInsightsService(new ObjectMapper(), "test-key", "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1, 2, 1200);

            ApiException exception = assertThrows(ApiException.class, () -> service.generateInsights(completeDashboard()));

            assertEquals(HttpStatus.BAD_GATEWAY, exception.status());
            assertEquals(temporaryProviderMessage(), exception.getMessage());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsValidStructuredToolCallOutput() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        ObjectMapper mapper = new ObjectMapper();
        String arguments = """
                {"overallAssessment":"Structured output.","insights":[{"title":"Add CI","priority":"High","explanation":"No workflow was detected.","evidence":["CI workflow is missing"],"recommendation":"Add a workflow."}],"recommendedNextSteps":["Add CI."],"limitations":"Metadata only."}
                """;
        String providerResponse = """
                {"choices":[{"finish_reason":"tool_calls","message":{"tool_calls":[{"type":"function","function":{"name":"submit_repository_insights","arguments":%s}}]}}]}
                """.formatted(mapper.writeValueAsString(arguments));
        HttpServer server = responseServer(providerResponse, 0, requests);
        try {
            GemmaInsightsService service = new GemmaInsightsService(mapper, "test-key", "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1, 2, 1200);

            AiInsightsDto result = service.generateInsights(completeDashboard());

            assertEquals("Structured output.", result.overallAssessment());
            assertEquals("Add CI", result.insights().getFirst().title());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsADeadlineCancelledResponseBodyToGatewayTimeoutWithoutRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = delayedBodyServer(requests);
        try {
            GemmaInsightsService service = new GemmaInsightsService(new ObjectMapper(), "test-key", "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1, 1, 1200);

            long started = System.nanoTime();
            ApiException exception = assertThrows(ApiException.class, () -> service.generateInsights(completeDashboard()));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

            assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.status());
            assertEquals(temporaryProviderMessage(), exception.getMessage());
            assertEquals(1, requests.get());
            assertTrue(elapsedMillis < 2_000, "body reading should obey the same request deadline");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer responseServer(String response, long delayMillis, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            try {
                exchange.getRequestBody().readAllBytes();
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private HttpServer delayedBodyServer(AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("{\"choices\":[".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                Thread.sleep(3_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private String temporaryProviderMessage() {
        return "AI service is temporarily unavailable. The service may be experiencing high traffic. Please try again in a little while.";
    }

    private GemmaInsightsService service() {
        return new GemmaInsightsService(new ObjectMapper(), "", "test-model", "http://127.0.0.1:1", 1, 1, 1200);
    }

    private DashboardDto dashboard() {
        return new DashboardDto(new DashboardDto.RepositoryDto("repository", "owner/repository", "owner", "", "",
                "", "", "main", "", "public", "", "", 0, 0, 0, 0),
                null, null, null, null, null, null, null, null, null, null, Instant.now());
    }

    private DashboardDto completeDashboard() {
        return new DashboardDto(new DashboardDto.RepositoryDto("repository", "owner/repository", "owner", "", "",
                "", "", "main", "", "public", "", "", 0, 0, 0, 0),
                new DashboardDto.HealthScoreDto(0, "C", "Needs attention", "", List.of()),
                new DashboardDto.QuickMetricsDto(0, 0, 0, 0, 0, 0, 0, "", ""), List.of(),
                new DashboardDto.ReadmeAnalysisDto(0, false, List.of(), List.of(), List.of(), List.of(), ""), List.of(),
                new DashboardDto.StructureInsightDto(0, List.of(), List.of(), List.of(), List.of()),
                new DashboardDto.ActivityDto("", "", 0, 0, 0, 0, "", false),
                new DashboardDto.CommunityDto(0, 0, 0, 0, 0, 0, false),
                new DashboardDto.DeveloperSnapshotDto("C", "", List.of(), List.of()), List.of(), Instant.now());
    }
}
