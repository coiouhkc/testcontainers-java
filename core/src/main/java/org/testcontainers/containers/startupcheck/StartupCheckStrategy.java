package org.testcontainers.containers.startupcheck;

import org.rnorth.ducttape.ratelimits.RateLimiter;
import org.rnorth.ducttape.ratelimits.RateLimiterBuilder;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.controller.ContainerController;
import org.testcontainers.controller.model.ContainerState;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.testcontainers.containers.GenericContainer.CONTAINER_RUNNING_TIMEOUT_SEC;

/**
 * Approach to determine whether a container has 'started up' correctly.
 */
public abstract class StartupCheckStrategy {

    private static final RateLimiter DOCKER_CLIENT_RATE_LIMITER = RateLimiterBuilder
            .newBuilder()
            .withRate(1, TimeUnit.SECONDS)
            .withConstantThroughput()
            .build();

    private Duration timeout = Duration.ofSeconds(CONTAINER_RUNNING_TIMEOUT_SEC);

    @SuppressWarnings("unchecked")
    public <SELF extends StartupCheckStrategy> SELF withTimeout(Duration timeout) {
        this.timeout = timeout;
        return (SELF) this;
    }

    public boolean waitUntilStartupSuccessful(ContainerController containerController, String containerId) {
        final Boolean[] startedOK = {null};
        Unreliables.retryUntilTrue((int) timeout.toMillis(), TimeUnit.MILLISECONDS, () -> {
            //noinspection CodeBlock2Expr
            return DOCKER_CLIENT_RATE_LIMITER.getWhenReady(() -> {
                StartupStatus state = checkStartupState(containerController, containerId);
                switch (state) {
                    case SUCCESSFUL:    startedOK[0] = true;
                                        return true;
                    case FAILED:        startedOK[0] = false;
                                        return true;
                    default:            return false;
                }
            });
        });
        return startedOK[0];
    }

    public abstract StartupStatus checkStartupState(ContainerController containerController, String containerId);

    protected ContainerState getCurrentState(ContainerController containerController, String containerId) {
        return containerController.inspectContainerIntent(containerId).perform().getState();
    }

    public enum StartupStatus {
        NOT_YET_KNOWN, SUCCESSFUL, FAILED
    }
}
