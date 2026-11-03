package com.example.orchestrator.context;

import com.example.orchestrator.memory.MemoryBundle;
import com.example.orchestrator.project.Project;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContextManager {

    private static final Set<String> EXCLUDE_DIRS = Set.of(
        ".git", "target", "build", "node_modules", "venv", ".venv", "__pycache__",
        ".idea", ".vscode", "out", "dist", "bin", "obj"
    );

    private static final Set<String> EXCLUDE_EXTENSIONS = Set.of(
        ".class", ".jar", ".war", ".ear", ".exe", ".dll", ".so", ".dylib",
        ".log", ".tmp", ".temp", ".cache", ".pid", ".lock"
    );

    private static final int MAX_FILES = 200;
    private static final int MAX_FILE_SIZE = 100 * 1024; // 100KB
    private static final int MAX_TOTAL_CONTEXT = 500 * 1024; // 500KB

    // TF-IDF style scoring
    private final Map<String, Integer> globalTermFrequency = new HashMap<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private int totalDocuments = 0;

    public ContextBundle selectContext(Project project, String request, MemoryBundle memoryBundle) throws IOException {
        List<ContextFile> candidateFiles = scanProjectFiles(project.rootPath());
        updateTermStatistics(candidateFiles);

        List<ContextFile> scoredFiles = candidateFiles.stream()
            .map(file -> scoreFileWithTfIdf(file, request))
            .filter(f -> f.score() > 0)
            .sorted(Comparator.comparingInt(ContextFile::score).reversed())
            .limit(MAX_FILES)
            .toList();

        int totalSize = 0;
        List<ContextFile> selectedFiles = new ArrayList<>();
        for (ContextFile file : scoredFiles) {
            if (totalSize + file.content().length() > MAX_TOTAL_CONTEXT) {
                break;
            }
            selectedFiles.add(file);
            totalSize += file.content().length();
        }

        // Prepend learned conventions from memory
        String priorityContext = memoryBundle.getPriorityContext();
        List<ContextFile> finalFiles = new ArrayList<>();
        if (priorityContext != null && !priorityContext.isBlank()) {
            finalFiles.add(new ContextFile("CONVENTION", priorityContext, 200));
        }
        finalFiles.addAll(selectedFiles);

        return new ContextBundle(
            project.id(),
            finalFiles,
            memoryBundle.getPriorityContext(),
            request
        );
    }

    private void updateTermStatistics(List<ContextFile> files) {
        totalDocuments = files.size();
        for (ContextFile file : files) {
            Set<String> uniqueTerms = new HashSet<>(extractTerms(file.content()));
            for (String term : uniqueTerms) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
            for (String term : extractTerms(file.content())) {
                globalTermFrequency.merge(term, 1, Integer::sum);
            }
        }
    }

    private ContextFile scoreFileWithTfIdf(ContextFile file, String request) {
        int score = 0;
        String requestLower = request.toLowerCase();
        String pathLower = file.path().toLowerCase();
        String contentLower = file.content().toLowerCase();

        // Extract request terms
        List<String> requestTerms = extractTerms(requestLower);
        if (requestTerms.isEmpty()) {
            return new ContextFile(file.path(), file.content(), 0);
        }

        // File name matches (high weight)
        for (String term : requestTerms) {
            if (pathLower.contains(term)) {
                score += 20;
            }
        }

        // Content matches with TF-IDF weighting
        Map<String, Integer> contentTermFreq = termFrequency(contentLower);
        for (String term : requestTerms) {
            int tf = contentTermFreq.getOrDefault(term, 0);
            if (tf > 0) {
                double idf = Math.log((double) totalDocuments / (documentFrequency.getOrDefault(term, 1)));
                score += (int) (tf * idf * 10);
            }
        }

        // Prefer certain file types
        if (file.path().endsWith(".java")) score += 5;
        if (file.path().contains("Controller")) score += 5;
        if (file.path().contains("Service")) score += 5;
        if (file.path().contains("Repository")) score += 3;
        if (file.path().contains("Config")) score += 3;

        return new ContextFile(file.path(), file.content(), score);
    }

    private List<String> extractTerms(String text) {
        Set<String> stopWords = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "are", "was", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "can", "need",
            "add", "create", "make", "update", "change", "modify", "fix", "remove",
            "delete", "implement", "improve", "refactor", "test", "debug", "deploy",
            "get", "set", "new", "this", "that", "then", "than", "its", "it",
            "if", "else", "while", "switch", "case", "return", "void", "int"
        );

        return Arrays.stream(text.split("[^a-zA-Z0-9_]+"))
            .filter(w -> w.length() > 2)
            .filter(w -> !stopWords.contains(w.toLowerCase()))
            .distinct()
            .limit(30)
            .toList();
    }

    private Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> freq = new HashMap<>();
        for (String term : extractTerms(text)) {
            freq.merge(term, 1, Integer::sum);
        }
        return freq;
    }

    private List<ContextFile> scanProjectFiles(Path rootPath) throws IOException {
        List<ContextFile> files = new ArrayList<>();
        
        Files.walk(rootPath)
            .filter(Files::isRegularFile)
            .filter(path -> !shouldExclude(path, rootPath))
            .filter(path -> path.toString().endsWith(".java") 
                || path.toString().endsWith(".xml")
                || path.toString().endsWith(".yml")
                || path.toString().endsWith(".yaml")
                || path.toString().endsWith(".properties")
                || path.toString().endsWith(".json")
                || path.toString().endsWith(".md")
                || path.toString().endsWith(".gradle")
                || path.toString().endsWith(".kt"))
            .limit(MAX_FILES * 2)
            .forEach(path -> {
                try {
                    if (Files.size(path) <= MAX_FILE_SIZE) {
                        String content = Files.readString(path);
                        String relativePath = rootPath.relativize(path).toString();
                        files.add(new ContextFile(relativePath, content));
                    }
                } catch (IOException e) {
                    // Skip unreadable files
                }
            });

        return files;
    }

    private boolean shouldExclude(Path path, Path rootPath) {
        Path relative = rootPath.relativize(path);
        for (Path element : relative) {
            String name = element.toString();
            if (EXCLUDE_DIRS.contains(name)) {
                return true;
            }
            if (name.startsWith(".") && !name.equals(".gitignore")) {
                return true;
            }
        }
        String fileName = path.getFileName().toString();
        for (String ext : EXCLUDE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}