package com.wsa.e2e;

import java.io.File;

/**
 * Windows Docker Toolbox leaves {@code docker-machine} on {@code PATH} and often
 * {@code DOCKER_MACHINE_NAME=default}. Testcontainers then prefers a dead VirtualBox daemon.
 * Prefer Docker Desktop's engine pipe when present (system properties; scrub PATH in the shell).
 */
final class DockerEnvironment {

    private static final String DESKTOP_LINUX_ENGINE = "npipe:////./pipe/dockerDesktopLinuxEngine";
    private static final String DESKTOP_LINUX_ENGINE_PIPE = "\\\\.\\pipe\\dockerDesktopLinuxEngine";

    private DockerEnvironment() {
    }

    static void preferDesktopOverToolbox() {
        String os = System.getProperty("os.name", "");
        if (!os.toLowerCase().contains("win")) {
            return;
        }
        if (!new File(DESKTOP_LINUX_ENGINE_PIPE).exists()) {
            return;
        }
        System.setProperty("DOCKER_HOST", DESKTOP_LINUX_ENGINE);
        System.setProperty("docker.host", DESKTOP_LINUX_ENGINE);
        System.setProperty("testcontainers.dockerhost.override", DESKTOP_LINUX_ENGINE);
    }
}
