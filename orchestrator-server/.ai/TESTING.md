# Testing

## Test Summary

### Test Coverage (32 tests, 1 skipped)

| Test Class | Tests | Status |
|------------|-------|--------|
| ContextManagerTest | 2 | PASS |
| ExecutionIntegrationTest | 1 | SKIPPED (requires API key) |
| OrchestratorApplicationTests | 1 | PASS |
| PermissionManagerTest | 4 | PASS |
| ProjectManagerTest | 5 | PASS |
| RepairLoopServiceTest | 4 | PASS |
| ListDirectoryToolTest | 2 | PASS |
| SecretScanToolTest | 2 | PASS |
| SemgrepScanToolTest | 1 | PASS |
| DependencyCheckToolTest | 1 | PASS |
| DockerBuildToolTest | 1 | PASS |
| TrivyScanToolTest | 1 | PASS |
| VerificationControllerTest | 3 | PASS |
| ProjectMemoryServiceTest | 4 | PASS |
| ProjectManagerTest | 5 | PASS |
| **Total** | **32** | **31 PASS, 1 SKIPPED** |

### Test Categories

#### Unit Tests (23 tests)
- ContextManager: file relevance scoring, exclusion filtering
- ProjectMemoryService: initialize, load, write, validation
- ProjectManager: register, get, list, unregister, lastAccessed
- PermissionManager: SAFE/CONTROLLED/APPROVAL_REQUIRED/HUMAN_ONLY
- RepairLoopService: config, session creation, status transitions
- ListDirectoryTool: listing, exclusion, path traversal rejection
- SecretScanTool: clean code, AWS key detection
- DependencyCheckTool: instantiation, spec validation
- DockerBuildTool: instantiation, spec validation
- TrivyScanTool: instantiation, spec validation
- VerificationController: config defaults, full config

#### Integration Tests (1 skipped)
- ExecutionIntegrationTest: end-to-end read-only task execution (skipped - requires OPENROUTER_API_KEY)

### Test Patterns

#### Unit Tests
- Use `@TempDir` for isolated filesystem
- Mock external dependencies where possible
- Test both success and failure paths
- Validate JSON serialization/deserialization

#### Integration Tests
- Use `@SpringBootTest` + `@ActiveProfiles("test")`
- Disabled by default if they require external services
- Real Maven/Semgrep/Trivy not required for unit tests

### Test Coverage Gaps
- RepairLoopService: integration test with real AI model
- Semgrep/DependencyCheck/Trivy: integration with actual tools
- Docker tools: require Docker daemon
- SecretScanTool: more secret pattern variations
- VerificationController: full pipeline integration
- ArtifactStore: concurrent access, large artifacts
- PermissionManager: custom policy registration

### CI/CD Considerations
- All tests run in ~55 seconds
- No external services required (except skipped integration test)
- `@ActiveProfiles("test")` disables CLI runner
- mvn verify: BUILD SUCCESS