package com.example.orchestrator.llm.provider;

import com.example.orchestrator.llm.ModelProvider;
import com.example.orchestrator.llm.CostTracker;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ModelCapabilities;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fake model provider for testing - returns canned responses without calling external APIs.
 * Used in test profile to avoid external API calls and costs.
 */
public class TestModelProvider implements ModelProvider {

    private static final String PROVIDER_NAME = "test";

    private final Map<String, String> cannedResponses;

    public TestModelProvider() {
        this.cannedResponses = Map.of(
                "clarifier",
                "[{\"id\":\"Q-001\",\"question\":\"Which database should be used?\",\"default\":\"PostgreSQL\",\"type\":\"SELECT\",\"options\":[\"PostgreSQL\",\"MySQL\",\"H2\"]},{\"id\":\"Q-002\",\"question\":\"What authentication type is needed?\",\"default\":\"JWT\",\"type\":\"SELECT\",\"options\":[\"JWT\",\"Session\",\"None\"]},{\"id\":\"Q-003\",\"question\":\"Should the API support pagination?\",\"default\":\"true\",\"type\":\"BOOLEAN\",\"options\":[]},{\"id\":\"Q-004\",\"question\":\"What is the primary entity name?\",\"default\":\"Book\",\"type\":\"INPUT\",\"options\":[]}]",
                "planner",
                "[{\"id\":\"T-001\",\"title\":\"Create Book entity\",\"description\":\"Create JPA entity for Book with title, author, genre, isbn fields\",\"files\":[\"src/main/java/com/example/library/domain/Book.java\"],\"dependsOn\":[],\"requirementIds\":[\"R-001\"],\"acceptance\":[\"Entity compiles\",\"Fields match requirements\",\"JPA annotations present\"]},{\"id\":\"T-002\",\"title\":\"Create Book repository\",\"description\":\"Create Spring Data JPA repository\",\"files\":[\"src/main/java/com/example/library/repository/BookRepository.java\"],\"dependsOn\":[\"T-001\"],\"requirementIds\":[\"R-001\"],\"acceptance\":[\"Repository compiles\",\"Extends JpaRepository\"]},{\"id\":\"T-003\",\"title\":\"Create Book service\",\"description\":\"Create service with CRUD operations\",\"files\":[\"src/main/java/com/example/library/service/BookService.java\"],\"dependsOn\":[\"T-002\"],\"requirementIds\":[\"R-002\"],\"acceptance\":[\"Service compiles\",\"CRUD methods implemented\",\"@Transactional present\"]},{\"id\":\"T-004\",\"title\":\"Create Book controller\",\"description\":\"Create REST controller with endpoints\",\"files\":[\"src/main/java/com/example/library/controller/BookController.java\"],\"dependsOn\":[\"T-003\"],\"requirementIds\":[\"R-002\"],\"acceptance\":[\"Controller compiles\",\"GET/POST/PUT/DELETE endpoints\",\"@Valid on DTOs\"]},{\"id\":\"T-005\",\"title\":\"Create unit tests\",\"description\":\"Create unit tests for service layer\",\"files\":[\"src/test/java/com/example/library/service/BookServiceTest.java\"],\"dependsOn\":[\"T-003\"],\"requirementIds\":[\"R-003\"],\"acceptance\":[\"Tests compile\",\"Coverage > 80%\"]}]",
                "refiner",
                "Goal: Generate a Spring Boot REST API\n" +
                        "Fixed Stack: Java 21, Spring Boot 3.4, Maven, PostgreSQL, JWT\n" +
                        "Entities: Book (id, title, author, genre, isbn)\n" +
                        "Endpoints: GET/POST /api/books, GET/PUT/DELETE /api/books/{id}\n" +
                        "Security Rules: JWT auth, BCrypt passwords, parameterized queries\n" +
                        "Validation Rules: @Valid on DTOs, ISBN format validation\n" +
                        "Testing Rules: JUnit 5, Testcontainers, 80% coverage\n" +
                        "Conventions: Constructor injection, records for DTOs, MapStruct\n" +
                        "Out of Scope: GraphQL, WebSocket, Admin UI\n" +
                        "Acceptance: mvn verify passes, 0 HIGH findings",
                "coder",
                "{\"tool\": \"writeFile\", \"arguments\": {\"path\": \"src/main/java/com/example/library/entity/Book.java\", \"content\": \"package com.example.library.entity;\\n\\nimport jakarta.persistence.*;\\nimport jakarta.validation.constraints.*;\\nimport java.util Objects;\\n\\n@Entity\\n@Table(name = \\\"books\\\")\\npublic class Book {\\n    @Id\\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\\n    private Long id;\\n\\n    @NotBlank\\n    @Size(max = 200)\\n    private String title;\\n\\n    @NotBlank\\n    @Size(max = 100)\\n    private String author;\\n\\n    @Size(max = 50)\\n    private String genre;\\n\\n    @Pattern(regexp = \\\"^(?:ISBN(?:-1[03])?:? )?(?=[0-9X]{10}$|(?=(?:[0-9]+[- ]){3})[- 0-9X]{13}$)[0-9]{1,5}[- ]?[0-9]+[- ]?[0-9]+[- ]?[0-9X]$\\\")\\n    private String isbn;\\n\\n    // constructors, getters, setters, equals, hashCode\\n}\"}}",
                "reviewer",
                "{\"findings\": [], \"summary\": \"Code looks good, follows conventions\"}",
                "documentation",
                "# Library API\n\n## Book Entity\n- id: Long (auto-generated)\n- title: String (required, max 200)\n- author: String (required, max 100)\n- genre: String (optional, max 50)\n- isbn: String (validated ISBN format)"
        );
    }

    @Override
    public String getProviderName() {
        return "test";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        String responseContent = cannedResponses.getOrDefault(
                request.metadata().getOrDefault("profile", "general").toString(),
                "{}"
        );

        return Mono.just(new ChatResponse(
                "test-response-" + System.currentTimeMillis(),
                request.model(),
                List.of(new ChatResponse.Choice(
                        0,
                        new ChatResponse.Choice.Message("assistant", responseContent, List.of()),
                        "stop"
                )),
                new ChatResponse.Usage(100, 50, 150),
                "test",
                Instant.now()
        ));
    }

    @Override
    public ModelCapabilities getCapabilities() {
        return new ModelCapabilities(
                "test",
                List.of("test-model"),
                4096,
                2048,
                false,
                false,
                false,
                Map.of("test-model", new ModelCapabilities.Pricing(0.0, 0.0))
        );
    }
}