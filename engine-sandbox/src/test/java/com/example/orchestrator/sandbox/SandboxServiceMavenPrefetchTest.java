package com.example.orchestrator.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the R-04 two-phase Maven flow: a cold offline build fails, a
 * read-only networked prefetch warms the cache, and the command is retried
 * with network disabled.
 */
class SandboxServiceMavenPrefetchTest {

    @TempDir
    Path tempDir;

    @Test
    void coldBuildWarmsDependenciesThenRetriesOffline() throws Exception {
        Path project = tempDir.resolve("library");
        Files.createDirectories(project);

        SandboxConfig config = new SandboxConfig();
        config.setWorkspaceRoots(List.of(tempDir.toString()));
        config.setEnableNetwork(false);
        config.setEnableDependencyPrefetch(true);
        config.setDependencyPrefetchTimeoutSeconds(60);

        SandboxService service = new SandboxService(config);

        DockerClient docker = mock(DockerClient.class);
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateVolumeCmd createVolumeCmd = mock(CreateVolumeCmd.class);
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        WaitContainerCmd waitCmd = mock(WaitContainerCmd.class);
        WaitContainerResultCallback waitCallback = mock(WaitContainerResultCallback.class);
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        PingCmd pingCmd = mock(PingCmd.class);

        when(docker.pingCmd()).thenReturn(pingCmd);
        when(docker.createVolumeCmd()).thenReturn(createVolumeCmd);
        when(createVolumeCmd.withName(anyString())).thenReturn(createVolumeCmd);
        when(createVolumeCmd.withLabels(any())).thenReturn(createVolumeCmd);
        when(createVolumeCmd.exec()).thenReturn(null);

        when(docker.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withCmd(any(String[].class))).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withWorkingDir(anyString())).thenReturn(createCmd);
        when(createCmd.withAttachStdout(anyBoolean())).thenReturn(createCmd);
        when(createCmd.withAttachStderr(anyBoolean())).thenReturn(createCmd);
        when(createCmd.withTty(anyBoolean())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(
            container("c1"), container("c2"), container("c3"));

        when(docker.startContainerCmd(anyString())).thenReturn(startCmd);
        when(docker.waitContainerCmd(anyString())).thenReturn(waitCmd);
        when(waitCmd.start()).thenReturn(waitCallback);
        when(waitCallback.awaitCompletion(anyLong(), any())).thenReturn(true);
        when(docker.removeContainerCmd(anyString())).thenReturn(removeCmd);
        when(removeCmd.withForce(anyBoolean())).thenReturn(removeCmd);

        InspectContainerCmd inspect1 = inspectCmd(1);
        InspectContainerCmd inspect2 = inspectCmd(0);
        InspectContainerCmd inspect3 = inspectCmd(0);
        LogContainerCmd log1 = logCmd(
            "[ERROR] Could not resolve dependencies for project com.example:library:jar:0.0.1");
        LogContainerCmd log2 = logCmd("[INFO] BUILD SUCCESS");
        LogContainerCmd log3 = logCmd("[INFO] BUILD SUCCESS");

        when(docker.inspectContainerCmd("c1")).thenReturn(inspect1);
        when(docker.inspectContainerCmd("c2")).thenReturn(inspect2);
        when(docker.inspectContainerCmd("c3")).thenReturn(inspect3);

        when(docker.logContainerCmd("c1")).thenReturn(log1);
        when(docker.logContainerCmd("c2")).thenReturn(log2);
        when(docker.logContainerCmd("c3")).thenReturn(log3);

        setDockerClient(service, docker);

        SandboxResult result = service.runMaven(
            List.of("mvn", "test"), project, ".", 60);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("BUILD SUCCESS");

        ArgumentCaptor<HostConfig> hostConfigs = ArgumentCaptor.forClass(HostConfig.class);
        verify(createCmd, times(3)).withHostConfig(hostConfigs.capture());

        List<HostConfig> phases = hostConfigs.getAllValues();
        assertThat(phases.get(0).getNetworkMode()).isEqualTo("none");
        assertThat(phases.get(1).getNetworkMode()).isEqualTo("bridge");
        assertThat(phases.get(2).getNetworkMode()).isEqualTo("none");

        assertThat(workspaceBind(phases.get(0)).getAccessMode()).isEqualTo(AccessMode.rw);
        assertThat(workspaceBind(phases.get(1)).getAccessMode()).isEqualTo(AccessMode.ro);
        assertThat(workspaceBind(phases.get(2)).getAccessMode()).isEqualTo(AccessMode.rw);

        ArgumentCaptor<String[]> commands = ArgumentCaptor.forClass(String[].class);
        verify(createCmd, times(3)).withCmd(commands.capture());
        assertThat(commands.getAllValues().get(0)).containsExactly("mvn", "test");
        assertThat(commands.getAllValues().get(1))
            .containsExactlyElementsOf(SandboxService.mavenPrefetchArguments());
        assertThat(commands.getAllValues().get(2)).containsExactly("mvn", "test");
    }

    @Test
    void nonDependencyFailuresAreNotRetriedWithNetwork() throws Exception {
        Path project = tempDir.resolve("library");
        Files.createDirectories(project);

        SandboxConfig config = new SandboxConfig();
        config.setWorkspaceRoots(List.of(tempDir.toString()));
        config.setEnableNetwork(false);
        config.setEnableDependencyPrefetch(true);

        SandboxService service = new SandboxService(config);

        DockerClient docker = mock(DockerClient.class);
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateVolumeCmd createVolumeCmd = mock(CreateVolumeCmd.class);
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        WaitContainerCmd waitCmd = mock(WaitContainerCmd.class);
        WaitContainerResultCallback waitCallback = mock(WaitContainerResultCallback.class);
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        PingCmd pingCmd = mock(PingCmd.class);

        when(docker.pingCmd()).thenReturn(pingCmd);
        when(docker.createVolumeCmd()).thenReturn(createVolumeCmd);
        when(createVolumeCmd.withName(anyString())).thenReturn(createVolumeCmd);
        when(createVolumeCmd.withLabels(any())).thenReturn(createVolumeCmd);
        when(createVolumeCmd.exec()).thenReturn(null);
        when(docker.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withCmd(any(String[].class))).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withWorkingDir(anyString())).thenReturn(createCmd);
        when(createCmd.withAttachStdout(anyBoolean())).thenReturn(createCmd);
        when(createCmd.withAttachStderr(anyBoolean())).thenReturn(createCmd);
        when(createCmd.withTty(anyBoolean())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(container("c1"));
        when(docker.startContainerCmd(anyString())).thenReturn(startCmd);
        when(docker.waitContainerCmd(anyString())).thenReturn(waitCmd);
        when(waitCmd.start()).thenReturn(waitCallback);
        when(waitCallback.awaitCompletion(anyLong(), any())).thenReturn(true);
        when(docker.removeContainerCmd(anyString())).thenReturn(removeCmd);
        when(removeCmd.withForce(anyBoolean())).thenReturn(removeCmd);
        InspectContainerCmd inspect = inspectCmd(1);
        LogContainerCmd log = logCmd("[ERROR] COMPILATION ERROR");
        when(docker.inspectContainerCmd("c1")).thenReturn(inspect);
        when(docker.logContainerCmd("c1")).thenReturn(log);

        setDockerClient(service, docker);

        SandboxResult result = service.runMaven(
            List.of("mvn", "test"), project, ".", 60);

        assertThat(result.exitCode()).isEqualTo(1);
        verify(createCmd, times(1)).exec();
    }

    private static CreateContainerResponse container(String id) {
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId(id);
        return response;
    }

    private static InspectContainerCmd inspectCmd(int exitCode) {
        InspectContainerCmd command = mock(InspectContainerCmd.class);
        InspectContainerResponse response = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state =
            mock(InspectContainerResponse.ContainerState.class);
        when(response.getState()).thenReturn(state);
        when(state.getExitCode()).thenReturn(exitCode);
        when(state.getOOMKilled()).thenReturn(false);
        when(command.exec()).thenReturn(response);
        return command;
    }

    private static LogContainerCmd logCmd(String stdout) {
        LogContainerCmd command = mock(LogContainerCmd.class);
        when(command.withStdOut(anyBoolean())).thenReturn(command);
        when(command.withStdErr(anyBoolean())).thenReturn(command);
        when(command.withFollowStream(anyBoolean())).thenReturn(command);
        when(command.withTimestamps(anyBoolean())).thenReturn(command);
        when(command.withTailAll()).thenReturn(command);
        doAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onStart(null);
            callback.onNext(new Frame(StreamType.STDOUT, stdout.getBytes(StandardCharsets.UTF_8)));
            callback.onComplete();
            return null;
        }).when(command).exec(any());
        return command;
    }

    private static Bind workspaceBind(HostConfig hostConfig) {
        return java.util.Arrays.stream(hostConfig.getBinds())
            .filter(bind -> "/workspace".equals(bind.getVolume().getPath()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("workspace bind missing"));
    }

    private static void setDockerClient(SandboxService service, DockerClient docker)
            throws ReflectiveOperationException {
        Field field = SandboxService.class.getDeclaredField("dockerClient");
        field.setAccessible(true);
        field.set(service, docker);
    }
}
