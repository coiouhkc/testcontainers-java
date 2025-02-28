package org.testcontainers.vault;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.controller.intents.InspectContainerResult;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.github.dockerjava.api.model.Capability.IPC_LOCK;


/**
 * GenericContainer subclass for Vault specific configuration and features. The main feature is the
 * withSecretInVault method, where users can specify which secrets to be pre-loaded into Vault for
 * their specific test scenario.
 * <p>
 * Other helpful features include the withVaultPort, and withVaultToken methods for convenience.
 */
public class VaultContainer<SELF extends VaultContainer<SELF>> extends GenericContainer<SELF> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("vault");
    private static final String DEFAULT_TAG = "1.1.3";

    private static final int VAULT_PORT = 8200;

    private Map<String, List<String>> secretsMap = new HashMap<>();
    private List<String> initCommands = new ArrayList<>();

    private int port = VAULT_PORT;

    /**
     * @deprecated use {@link VaultContainer(DockerImageName)} instead
     */
    @Deprecated
    public VaultContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    public VaultContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public VaultContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);

        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        // Use the vault healthcheck endpoint to check for readiness, per https://www.vaultproject.io/api/system/health.html
        setWaitStrategy(Wait.forHttp("/v1/sys/health").forStatusCode(200));

        withCreateContainerCmdModifier(cmd -> cmd.withCapAdd(IPC_LOCK));
        withEnv("VAULT_ADDR", "http://0.0.0.0:" + port);
        withExposedPorts(port);
    }

    @Override
    protected void containerIsStarted(InspectContainerResult containerInfo) {
        addSecrets();
        runInitCommands();
    }

    private void addSecrets() {
        if (!secretsMap.isEmpty()) {
            try {
                this.execInContainer(buildExecCommand(secretsMap)).getStdout().contains("Success");
            } catch (IOException | InterruptedException e) {
                logger().error("Failed to add these secrets {} into Vault via exec command. Exception message: {}", secretsMap, e.getMessage());
            }
        }
    }

    private String[] buildExecCommand(Map<String, List<String>> map) {
        StringBuilder stringBuilder = new StringBuilder();
        map.forEach((path, secrets) -> {
            stringBuilder.append(" && vault kv put " + path);
            secrets.forEach(item -> stringBuilder.append(" " + item));
        });
        return new String[]{"/bin/sh", "-c", stringBuilder.toString().substring(4)};
    }

    private void runInitCommands() {
        if (!initCommands.isEmpty()) {
            String commands = initCommands.stream()
                                    .map(command -> "vault " + command)
                                    .collect(Collectors.joining(" && "));
            try {
                ExecResult execResult = this.execInContainer(new String[]{"/bin/sh", "-c", commands});
                if (execResult.getExitCode() != 0) {
                    logger().error("Failed to execute these init commands {}. Exit code {}. Stdout {}. Stderr {}",
                                    initCommands, execResult.getExitCode(), execResult.getStdout(), execResult.getStderr());
                }
            } catch (IOException | InterruptedException e) {
                logger().error("Failed to execute these init commands {}. Exception message: {}", initCommands, e.getMessage());
            }
        }
    }

    /**
     * Sets the Vault root token for the container so application tests can source secrets using the token
     *
     * @param token the root token value to set for Vault.
     * @return this
     */
    public SELF withVaultToken(String token) {
        withEnv("VAULT_DEV_ROOT_TOKEN_ID", token);
        withEnv("VAULT_TOKEN", token);
        return self();
    }

    /**
     * Sets the Vault port in the container as well as the port bindings for the host to reach the container over HTTP.
     *
     * @param port the port number you want to have the Vault container listen on for tests.
     * @return this
     * @deprecated the exposed port will be randomized automatically. As calling this method provides no additional value, you are recommended to remove the call. getFirstMappedPort() may be used to obtain the listening vault port.
     */
    @Deprecated
    public SELF withVaultPort(int port) {
        this.port = port;
        return self();
    }

    /**
     * Sets the logging level for the Vault server in the container.
     * Logs can be consumed through {@link #withLogConsumer(Consumer)}.
     *
     * @param level the logging level to set for Vault.
     * @return this
     */
    public SELF withLogLevel(VaultLogLevel level) {
        return withEnv("VAULT_LOG_LEVEL", level.config);
    }

    /**
     * Pre-loads secrets into Vault container. User may specify one or more secrets and all will be added to each path
     * that is specified. Thus this can be called more than once for multiple paths to be added to Vault.
     * <p>
     * The secrets are added to vault directly after the container is up via the
     * {@link #addSecrets() addSecrets}, called from {@link #containerIsStarted(InspectContainerResult) containerIsStarted}
     *
     * @param path             specific Vault path to store specified secrets
     * @param firstSecret      first secret to add to specifed path
     * @param remainingSecrets var args list of secrets to add to specified path
     * @return this
     */
    public SELF withSecretInVault(String path, String firstSecret, String... remainingSecrets) {
        List<String> list = new ArrayList<>();
        list.add(firstSecret);
        for (String secret : remainingSecrets) {
            list.add(secret);
        }
        if (secretsMap.containsKey(path)) {
            list.addAll(list);
        }
        secretsMap.putIfAbsent(path, list);
        return self();
    }

    /**
     * Run initialization commands using the vault cli.
     *
     * Useful for enableing more secret engines like:
     * <pre>
     *     .withInitCommand("secrets enable pki")
     *     .withInitCommand("secrets enable transit")
     * </pre>
     * @param commands The commands to send to the vault cli
     * @return this
     */
    public SELF withInitCommand(String... commands) {
        initCommands.addAll(Arrays.asList(commands));
        return self();
    }
}
