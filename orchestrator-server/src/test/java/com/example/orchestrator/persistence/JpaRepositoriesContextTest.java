package com.example.orchestrator.persistence;

import com.example.orchestrator.persistence.repo.TaskCostRepository;
import com.example.orchestrator.store.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real application context with H2 and real JPA repositories.
 *
 * <p>Regression guard for Boot 4's changed entity scanning: when
 * {@code @EnableJpaRepositories} is declared explicitly, the entity packages are
 * no longer derived from the application package. Mocked repository tests do not
 * catch that failure mode; this test does.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class JpaRepositoriesContextTest {

    @Autowired
    private TaskCostRepository taskCostRepository;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void serverAndEngineStoreRepositoriesShareTheJpaMetamodel() {
        assertThat(taskCostRepository.count()).isZero();
        assertThat(jobRepository.count()).isZero();
    }
}
