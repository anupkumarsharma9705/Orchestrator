package com.example.orchestrator.store;

import com.example.orchestrator.store.entity.FindingEntity;
import com.example.orchestrator.store.entity.JobEntity;
import com.example.orchestrator.store.entity.RoundEntity;
import com.example.orchestrator.store.entity.CostEntity;
import com.example.orchestrator.store.repository.JobRepository;
import com.example.orchestrator.store.repository.RoundRepository;
import com.example.orchestrator.store.repository.FindingRepository;
import com.example.orchestrator.store.repository.CostRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Test configuration for Engine Store.
 * Sets up H2 in-memory database with JPA/Hibernate.
 */
@Configuration
@Profile("test")
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.example.orchestrator.store.repository")
@EntityScan(basePackages = "com.example.orchestrator.store.entity")
public class StoreTestConfig {

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:db/migration/V1__initial_schema.sql")
            .addScript("classpath:db/migration/V2__memory_and_approvals.sql")
            .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(false);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setPackagesToScan("com.example.orchestrator.store.entity");
        
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        jpaProperties.put("hibernate.hbm2ddl.auto", "create-drop");
        jpaProperties.put("hibernate.show_sql", "false");
        jpaProperties.put("hibernate.format_sql", "true");
        factory.setJpaProperties(jpaProperties);
        
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory.getObject());
        return txManager;
    }

    @Bean
    public ArtifactStore artifactStore(
            JobRepository jobRepository,
            RoundRepository roundRepository,
            FindingRepository findingRepository,
            CostRepository costRepository) {
        return new ArtifactStore(jobRepository, roundRepository, findingRepository, costRepository);
    }
}