package org.testcontainers.utility;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.controller.ContainerController;
import org.testcontainers.controller.intents.LogContainerIntent;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDOUT;

/**
 * Provides utility methods for logging.
 */
@UtilityClass
public class LogUtils {

    /**
     * Attach a log consumer to a container's log outputs in follow mode. The consumer will receive all previous
     * and all future log frames of the specified type(s).
     *
     * @param containerController a Docker client
     * @param containerId  container ID to attach to
     * @param consumer     a consumer of {@link OutputFrame}s
     * @param types        types of {@link OutputFrame} to receive
     */
    public void followOutput(ContainerController containerController,
                             String containerId,
                             Consumer<OutputFrame> consumer,
                             OutputFrame.OutputType... types) {

        attachConsumer(containerController, containerId, consumer, true, types);
    }

    /**
     * Attach a log consumer to a container's log outputs in follow mode. The consumer will receive all previous
     * and all future log frames (both stdout and stderr).
     *
     * @param containerController a Docker client
     * @param containerId  container ID to attach to
     * @param consumer     a consumer of {@link OutputFrame}s
     */
    public void followOutput(ContainerController containerController,
                             String containerId,
                             Consumer<OutputFrame> consumer) {

        followOutput(containerController, containerId, consumer, STDOUT, STDERR);
    }

    /**
     * Retrieve all previous log outputs for a container of the specified type(s).
     *
     * @param containerController a Docker client
     * @param containerId  container ID to attach to
     * @param types        types of {@link OutputFrame} to receive
     * @return all previous output frames (stdout/stderr being separated by newline characters)
     */
    @SneakyThrows(IOException.class)
    public String getOutput(ContainerController containerController,
                            String containerId,
                            OutputFrame.OutputType... types) {

        if (containerId == null) {
            return "";
        }

        if (types.length == 0) {
            types = new OutputFrame.OutputType[] { STDOUT, STDERR };
        }

        final ToStringConsumer consumer = new ToStringConsumer();
        final WaitingConsumer wait = new WaitingConsumer();
        try (Closeable closeable = attachConsumer(containerController, containerId, consumer.andThen(wait), false, types)) {
            wait.waitUntilEnd();
            return consumer.toUtf8String();
        }
    }

    private static Closeable attachConsumer(
        ContainerController containerController,
        String containerId,
        Consumer<OutputFrame> consumer,
        boolean followStream,
        OutputFrame.OutputType... types
    ) {

        final LogContainerIntent cmd = containerController.logContainerIntent(containerId)
            .withFollowStream(followStream)
            .withSince(0);

        final FrameConsumerResultCallback callback = new FrameConsumerResultCallback();
        for (OutputFrame.OutputType type : types) {
            callback.addConsumer(type, consumer);
            if (type == STDOUT) cmd.withStdOut(true);
            if (type == STDERR) cmd.withStdErr(true);
        }

        return cmd.perform(callback);
    }
}
