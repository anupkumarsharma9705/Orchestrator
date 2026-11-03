package com.example.orchestrator.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
public class GitHubService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;
    private final String githubApiUrl;

    public GitHubService(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${github.token:}") String githubToken,
            @Value("${github.api-url:https://api.github.com}") String githubApiUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.githubToken = githubToken;
        this.githubApiUrl = githubApiUrl;
    }

    public boolean isConfigured() {
        return githubToken != null && !githubToken.isBlank() && !githubToken.equals("${GITHUB_TOKEN}");
    }

    public String createPullRequest(
            String owner,
            String repo,
            String title,
            String body,
            String headBranch,
            String baseBranch,
            Map<String, String> files) throws IOException {

        if (!isConfigured()) {
            throw new IllegalStateException("GitHub token not configured. Set GITHUB_TOKEN environment variable.");
        }

        // Validate inputs
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("Owner and repo are required");
        }
        if (!owner.matches("^[a-zA-Z0-9_-]+$") || !repo.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid owner or repo name");
        }

        // 1. Get base branch SHA
        String baseSha = getBranchSha(owner, repo, baseBranch);
        if (baseSha == null) {
            throw new IOException("Failed to get base branch SHA for: " + baseBranch);
        }

        // 2. Create new branch from base
        String refName = "refs/heads/" + headBranch;
        createRef(owner, repo, refName, baseSha);

        // 3. Create/update files in the new branch
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            createOrUpdateFile(owner, repo, path, content, headBranch, "Add/Update " + path);
        }

        // 4. Create pull request
        return createPullRequest(owner, repo, title, body, headBranch, baseBranch);
    }

    private String getBranchSha(String owner, String repo, String branch) throws IOException {
        Request request = new Request.Builder()
                .url(githubApiUrl + "/repos/" + owner + "/" + repo + "/git/ref/heads/" + branch)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            String json = response.body().string();
            JsonNode node = objectMapper.readTree(json);
            return node.path("object").path("sha").asText();
        }
    }

    private void createRef(String owner, String repo, String ref, String sha) throws IOException {
        Map<String, Object> body = Map.of("ref", ref, "sha", sha);
        String json = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(githubApiUrl + "/repos/" + owner + "/" + repo + "/git/refs")
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 422) { // 422 = already exists
                String responseBody = response.body().string();
                throw new IOException("Failed to create ref: " + response.code() + " " + responseBody);
            }
        }
    }

    private void createOrUpdateFile(String owner, String repo, String path, String content, String branch, String message) throws IOException {
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());

        Map<String, Object> body = Map.of(
                "message", message,
                "content", encodedContent,
                "branch", branch
        );
        String json = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(githubApiUrl + "/repos/" + owner + "/" + repo + "/contents/" + path)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .put(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String bodyStr = response.body().string();
                if (response.code() == 422 && bodyStr.contains("already exists")) {
                    // File exists, try to update with SHA
                    String fileSha = getFileSha(owner, repo, path, branch);
                    if (fileSha != null) {
                        updateFile(owner, repo, path, content, branch, fileSha, "Update " + path);
                        return;
                    }
                }
                throw new IOException("Failed to create/update file: " + response.code() + " " + bodyStr);
            }
        }
    }

    private String getFileSha(String owner, String repo, String path, String branch) throws IOException {
        Request request = new Request.Builder()
                .url(githubApiUrl + "/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + branch)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            String json = response.body().string();
            JsonNode node = objectMapper.readTree(json);
            return node.path("sha").asText();
        }
    }

    private void updateFile(String owner, String repo, String path, String content, String branch, String sha, String message) throws IOException {
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());

        Map<String, Object> body = Map.of(
                "message", message,
                "content", encodedContent,
                "branch", branch,
                "sha", sha
        );
        String json = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(githubApiUrl + "/repos/" + owner + "/" + repo + "/contents/" + path)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .put(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String bodyStr = response.body().string();
                throw new IOException("Failed to update file: " + response.code() + " " + bodyStr);
            }
        }
    }

    private String createPullRequest(String owner, String repo, String title, String body, String head, String base) throws IOException {
        Map<String, Object> prBody = Map.of(
                "title", title,
                "body", body,
                "head", head,
                "base", base
        );
        String json = objectMapper.writeValueAsString(prBody);

        Request request = new Request.Builder()
                .url(githubApiUrl + "/repos/" + owner + "/" + repo + "/pulls")
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body().string();
                throw new IOException("Failed to create PR: " + response.code() + " " + responseBody);
            }
            String responseJson = response.body().string();
            JsonNode node = objectMapper.readTree(responseJson);
            return node.path("html_url").asText();
        }
    }
}