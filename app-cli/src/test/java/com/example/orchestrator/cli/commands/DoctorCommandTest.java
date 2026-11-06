package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.config.CliConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorCommandTest {

    @Test
    void defaultServerUrl_isLocalOrchestratorEndpoint() {
        assertThat(DoctorCommand.isLocalOrchestratorEndpoint(CliConfig.defaults())).isTrue();
    }

    @Test
    void localhostHosts_areLocalOrchestratorEndpoints() {
        assertThat(DoctorCommand.isLocalOrchestratorEndpoint(config("https://localhost:8443"))).isTrue();
        assertThat(DoctorCommand.isLocalOrchestratorEndpoint(config("https://127.0.0.1:8443"))).isTrue();
        assertThat(DoctorCommand.isLocalOrchestratorEndpoint(config("https://[::1]:8443"))).isTrue();
    }

    @Test
    void remoteOrNonDefaultPort_isNotLocalOrchestratorEndpoint() {
        assertThat(DoctorCommand.isLocalOrchestratorEndpoint(config("https://orchestrator.example.com:8443"))).isFalse();
        assertThat(DoctorCommand.isLocalOrchestratorEndpoint(config("https://localhost:9000"))).isFalse();
    }

    @Test
    void invalidServerUrl_isNotLocalOrchestratorEndpoint() {
        assertThat(DoctorCommand.isLocalOrchestratorEndpoint(config("not a url"))).isFalse();
    }

    private static CliConfig config(String serverUrl) {
        return new CliConfig(serverUrl, "", "table", 600, "", "INFO", false, CliConfig.SslConfig.defaults());
    }
}
