package com.reporadar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.reporadar.client.GithubClient;
import com.reporadar.dto.DashboardDto;
import com.reporadar.exception.ApiException;
import com.reporadar.model.RepositoryReference;
import com.reporadar.utils.GithubJson;
import com.reporadar.utils.RepositoryUrlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RepositoryAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisService.class);
    private static final List<String> LANGUAGE_COLORS = List.of("#f97360", "#0f766e", "#0284c7", "#d97706", "#7c3aed", "#16a34a", "#db2777");
    private static final Duration AI_CACHE_TTL = Duration.ofMinutes(2);
    private final RepositoryUrlParser urlParser;
    private final GithubClient githubClient;
    private final ReadmeAnalysisService readmeAnalysisService;
    private final TechnologyDetectionService technologyDetectionService;
    private final HealthScoreService healthScoreService;
    private final SuggestionService suggestionService;
    private final Map<String, CachedAnalysis> recentAnalyses = new ConcurrentHashMap<>();

    public RepositoryAnalysisService(RepositoryUrlParser urlParser, GithubClient githubClient, ReadmeAnalysisService readmeAnalysisService,
                                     TechnologyDetectionService technologyDetectionService, HealthScoreService healthScoreService,
                                     SuggestionService suggestionService) {
        this.urlParser = urlParser;
        this.githubClient = githubClient;
        this.readmeAnalysisService = readmeAnalysisService;
        this.technologyDetectionService = technologyDetectionService;
        this.healthScoreService = healthScoreService;
        this.suggestionService = suggestionService;
    }

    public DashboardDto analyze(String repositoryUrl) {
        long started = System.nanoTime();
        RepositoryReference reference = urlParser.parse(repositoryUrl);
        log.info("Analyzing public repository {}", reference.fullName());
        JsonNode repository = githubClient.repository(reference);
        if (repository.path("private").asBoolean(false)) throw new ApiException(HttpStatus.FORBIDDEN, "This repository is private. RepoRadar can analyze public repositories only.");
        // These GitHub endpoints do not depend on each other. Fetching them serially made a
        // first analysis vulnerable to accumulating normal upstream latency past the old 30s
        // browser request limit. Keep the same bounded client timeouts, but overlap the calls.
        CompletableFuture<JsonNode> languagesFuture = supplyAsync(() -> githubClient.languages(reference));
        CompletableFuture<Optional<JsonNode>> readmeFuture = supplyAsync(() -> githubClient.readme(reference));
        CompletableFuture<JsonNode> releasesFuture = supplyAsync(() -> githubClient.releases(reference));
        CompletableFuture<JsonNode> contributorsFuture = supplyAsync(() -> githubClient.contributors(reference));
        CompletableFuture<JsonNode> branchesFuture = supplyAsync(() -> githubClient.branches(reference));
        CompletableFuture<JsonNode> pullsFuture = supplyAsync(() -> githubClient.pulls(reference));
        CompletableFuture<JsonNode> commitsFuture = supplyAsync(() -> githubClient.commits(reference));
        CompletableFuture<Optional<JsonNode>> treeFuture = supplyAsync(() -> githubClient.tree(reference,
                GithubJson.text(repository, "default_branch", "main")));

        try {
            CompletableFuture.allOf(languagesFuture, readmeFuture, releasesFuture, contributorsFuture,
                    branchesFuture, pullsFuture, commitsFuture, treeFuture).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof ApiException apiException) throw apiException;
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GitHub could not complete the request. Please try again shortly.");
        }
        JsonNode languages = languagesFuture.join();
        String readme = readmeFuture.join().map(this::decodeReadme).orElse("");
        JsonNode releases = releasesFuture.join();
        JsonNode contributors = contributorsFuture.join();
        JsonNode branches = branchesFuture.join();
        JsonNode pulls = pullsFuture.join();
        JsonNode commits = commitsFuture.join();
        List<String> files = treeFuture.join().map(this::extractFiles).orElseGet(List::of);

        Instant updatedAt = GithubJson.instant(repository, "updated_at");
        String description = GithubJson.text(repository, "description", "");
        DashboardDto.ReadmeAnalysisDto readmeAnalysis = readmeAnalysisService.analyze(readme, description);
        DashboardDto.StructureInsightDto structure = structure(files);
        List<DashboardDto.TechnologyDto> technologies = technologyDetectionService.detect(files, readme);
        boolean hasLicense = !repository.path("license").isMissingNode() && !repository.path("license").isNull();
        boolean hasWorkflow = files.stream().anyMatch(file -> file.startsWith(".github/workflows/"));
        int contributorCount = arraySize(contributors);
        DashboardDto.HealthScoreDto health = healthScoreService.calculate(readmeAnalysis, structure.score(), updatedAt,
                GithubJson.number(repository, "stargazers_count"), GithubJson.number(repository, "forks_count"), contributorCount,
                hasLicense, arraySize(releases), technologies.size(), !description.isBlank());

        DashboardDto.RepositoryDto repositoryDto = new DashboardDto.RepositoryDto(
                GithubJson.text(repository, "name", reference.name()), GithubJson.text(repository, "full_name", reference.fullName()), reference.owner(),
                GithubJson.text(repository.path("owner"), "avatar_url", ""), description, GithubJson.text(repository, "html_url", repositoryUrl),
                GithubJson.text(repository, "clone_url", ""), GithubJson.text(repository, "default_branch", "main"),
                hasLicense ? GithubJson.text(repository.path("license"), "spdx_id", GithubJson.text(repository.path("license"), "name", "Licensed")) : "Not specified",
                GithubJson.text(repository, "visibility", "public"), GithubJson.text(repository, "created_at", ""), GithubJson.text(repository, "updated_at", ""),
                GithubJson.number(repository, "stargazers_count"), GithubJson.number(repository, "forks_count"), GithubJson.number(repository, "subscribers_count"), GithubJson.number(repository, "open_issues_count"));
        DashboardDto.QuickMetricsDto metrics = new DashboardDto.QuickMetricsDto(repositoryDto.stars(), repositoryDto.forks(), repositoryDto.openIssues(),
                repositoryDto.watchers(), contributorCount, GithubJson.number(repository, "size"), arraySize(pulls), latestRelease(releases), latestCommit(commits));
        DashboardDto.ActivityDto activity = new DashboardDto.ActivityDto(latestCommit(commits), repositoryDto.updatedAt(),
                (int) Math.max(0, Duration.between(GithubJson.instant(repository, "created_at"), Instant.now()).toDays()), arraySize(releases),
                arraySize(branches), contributorCount, commitFrequency(updatedAt), Duration.between(updatedAt, Instant.now()).toDays() > 365);
        DashboardDto.CommunityDto community = new DashboardDto.CommunityDto(repositoryDto.stars(), repositoryDto.forks(), repositoryDto.watchers(), contributorCount,
                repositoryDto.openIssues(), communityScore(repositoryDto.stars(), repositoryDto.forks(), contributorCount), false);
        List<DashboardDto.SuggestionDto> suggestions = suggestionService.generate(readmeAnalysis, files, hasLicense, arraySize(releases), hasWorkflow);
        DashboardDto.DeveloperSnapshotDto snapshot = snapshot(health, readmeAnalysis, structure, activity, suggestions);
        DashboardDto result = new DashboardDto(repositoryDto, health, metrics, languageDtos(languages), readmeAnalysis, technologies, structure,
                activity, community, snapshot, suggestions, Instant.now());
        recentAnalyses.put(reference.fullName().toLowerCase(Locale.ROOT), new CachedAnalysis(result, Instant.now()));
        log.info("Completed analysis for {} in {} ms", reference.fullName(), (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    /** Reuses only a very recent successful dashboard result for the follow-up AI request. */
    public DashboardDto analyzeForAi(String repositoryUrl) {
        RepositoryReference reference = urlParser.parse(repositoryUrl);
        CachedAnalysis cached = recentAnalyses.get(reference.fullName().toLowerCase(Locale.ROOT));
        if (cached != null && Duration.between(cached.createdAt(), Instant.now()).compareTo(AI_CACHE_TTL) <= 0) {
            return cached.dashboard();
        }
        return analyze(repositoryUrl);
    }

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier);
    }

    private record CachedAnalysis(DashboardDto dashboard, Instant createdAt) { }

    private String decodeReadme(JsonNode readme) {
        String content = GithubJson.text(readme, "content", "").replaceAll("\\s", "");
        try { return new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException exception) { return ""; }
    }

    private List<String> extractFiles(JsonNode tree) {
        List<String> files = new ArrayList<>();
        tree.path("tree").forEach(item -> { if ("blob".equals(item.path("type").asText())) files.add(item.path("path").asText()); });
        return files;
    }

    private DashboardDto.StructureInsightDto structure(List<String> files) {
        List<String> folders = files.stream().filter(file -> file.contains("/")).map(file -> file.substring(0, file.indexOf('/'))).distinct().sorted().limit(12).toList();
        String corpus = String.join("\n", files).toLowerCase(Locale.ROOT);
        List<String> good = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        int score = 35;
        if (!folders.isEmpty()) { good.add("Source files are organized into top-level folders."); score += 20; }
        if (corpus.contains("readme")) { good.add("A README file provides a project entry point."); score += 10; } else missing.add("README.md");
        if (corpus.contains("license")) { good.add("A license file is present."); score += 10; } else missing.add("LICENSE");
        if (corpus.contains(".github/workflows")) { good.add("Automation is configured with GitHub Actions."); score += 15; } else { missing.add("CI workflow"); improvements.add("Add a GitHub Actions workflow to validate changes automatically."); }
        if (corpus.contains("test") || corpus.contains("spec")) { good.add("Test-related files or folders were detected."); score += 10; } else { missing.add("Tests"); improvements.add("Create a focused test suite for core behavior."); }
        if (folders.isEmpty()) improvements.add("Group source code into clear folders as the project grows.");
        return new DashboardDto.StructureInsightDto(Math.min(score, 100), good, missing, improvements, folders);
    }

    private List<DashboardDto.LanguageDto> languageDtos(JsonNode languages) {
        Map<String, Long> values = new LinkedHashMap<>();
        languages.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asLong()));
        long total = values.values().stream().mapToLong(value -> value.longValue()).sum();
        return values.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).map(entry ->
                new DashboardDto.LanguageDto(entry.getKey(), entry.getValue(), total == 0 ? 0 : Math.round(entry.getValue() * 10000d / total) / 100d,
                        LANGUAGE_COLORS.get(Math.floorMod(entry.getKey().hashCode(), LANGUAGE_COLORS.size())))).toList();
    }

    private int arraySize(JsonNode value) { return value != null && value.isArray() ? value.size() : 0; }
    private String latestRelease(JsonNode releases) { return arraySize(releases) == 0 ? "No releases" : GithubJson.text(releases.get(0), "tag_name", "Latest release"); }
    private String latestCommit(JsonNode commits) { return arraySize(commits) == 0 ? "No recent commit data" : GithubJson.text(commits.get(0).path("commit"), "message", "Latest commit").lines().findFirst().orElse("Latest commit"); }
    private String commitFrequency(Instant updatedAt) { long days = Duration.between(updatedAt, Instant.now()).toDays(); return days <= 30 ? "Recently maintained" : days <= 180 ? "Maintained this year" : "Limited recent activity"; }
    private int communityScore(long stars, long forks, int contributors) { return Math.min(100, (int) Math.round(stars * .45 + forks * .8 + contributors * 5)); }

    private DashboardDto.DeveloperSnapshotDto snapshot(DashboardDto.HealthScoreDto health, DashboardDto.ReadmeAnalysisDto readme,
                                                        DashboardDto.StructureInsightDto structure, DashboardDto.ActivityDto activity,
                                                        List<DashboardDto.SuggestionDto> suggestions) {
        List<String> strengths = new ArrayList<>();
        if (readme.score() >= 70) strengths.add("Useful documentation");
        if (structure.score() >= 70) strengths.add("Clear project structure");
        if (!activity.inactive()) strengths.add("Recent maintenance activity");
        if (strengths.isEmpty()) strengths.add("A visible public repository");
                List<String> needs = suggestions.stream().filter(item -> !"Low".equals(item.priority())).map(item -> item.title()).limit(3).toList();
        return new DashboardDto.DeveloperSnapshotDto(health.grade(), "This repository earns a " + health.grade() + " based on transparent, deterministic repository-health signals.", strengths, needs);
    }
}
