# JavaLab execution runner

This private service accepts bounded Java run jobs from Spring Boot and executes each job in a fresh Docker container. Run the service as a non-root host user on an isolated execution host. Do not expose port 8091 outside the private service network.

## Build the fixed student runtime image

    docker build --pull -t javalab-java-runner:21 runner-image

The image contains the Java 21 compiler/runtime. Each source file is mounted from a temporary per-run directory. The run container has no network, a read-only root filesystem, dropped capabilities, no-new-privileges, a non-root UID/GID, memory/CPU/PID limits, bounded writable tmpfs mounts, bounded output, and a five-second total timeout.

## Run the service

    export JAVALAB_RUNNER_TOKEN='replace-with-a-long-random-private-token'
    mvn spring-boot:run

Set the same token and JAVALAB_RUNNER_URL in the main application. It binds to loopback by default. In campus deployment bind only to a private interface and use firewall rules, a secret token, and an isolated VM. The runner requires Docker CLI access to a Docker daemon; this sensitive permission must not be granted to the web application.
