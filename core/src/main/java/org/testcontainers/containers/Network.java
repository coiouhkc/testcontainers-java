package org.testcontainers.containers;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.testcontainers.ContainerControllerFactory;
import org.testcontainers.controller.intents.CreateNetworkIntent;
import org.testcontainers.docker.DockerClientFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface Network extends AutoCloseable, TestRule {

    Network SHARED = new NetworkImpl(false, null, Collections.emptySet(), null) {
        @Override
        public void close() {
            // Do not allow users to close SHARED network, only ResourceReaper is allowed to close (destroy) it
        }
    };

    String getId();

    @Override
    void close();

    static Network newNetwork() {
        return builder().build();
    }

    static NetworkImpl.NetworkImplBuilder builder() {
        return NetworkImpl.builder();
    }

    @Builder
    @Getter
    class NetworkImpl extends ExternalResource implements Network {

        private final String name = UUID.randomUUID().toString();

        private Boolean enableIpv6;

        private String driver;

        @Singular
        private Set<Consumer<CreateNetworkIntent>> createNetworkCmdModifiers;

        @Deprecated
        private String id;

        private final AtomicBoolean initialized = new AtomicBoolean();

        @Override
        public synchronized String getId() {
            if (initialized.compareAndSet(false, true)) {
                id = create();
            }

            return id;
        }

        private String create() {
            CreateNetworkIntent createNetworkCmd = ContainerControllerFactory.instance().controller().createNetworkIntent(); // TODO: Rename

            createNetworkCmd.withName(name);
            createNetworkCmd.withCheckDuplicate(true);

            if (enableIpv6 != null) {
                createNetworkCmd.withEnableIpv6(enableIpv6);
            }

            if (driver != null) {
                createNetworkCmd.withDriver(driver);
            }

            for (Consumer<CreateNetworkIntent> consumer : createNetworkCmdModifiers) {
                consumer.accept(createNetworkCmd);
            }

            Map<String, String> labels = createNetworkCmd.getLabels();
            labels = new HashMap<>(labels != null ? labels : Collections.emptyMap());
            labels.putAll(DockerClientFactory.DEFAULT_LABELS);
            createNetworkCmd.withLabels(labels);

            return createNetworkCmd.perform().getId();
        }

        @Override
        protected void after() {
            close();
        }

        @Override
        public synchronized void close() {
            if (initialized.getAndSet(false)) {
                ContainerControllerFactory.instance().controller().getResourceReaper().removeNetworkById(id);
            }
        }
    }
}
