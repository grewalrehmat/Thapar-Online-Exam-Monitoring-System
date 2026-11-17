package com.javalab.monitoring;

import jakarta.validation.constraints.Size;

public record CameraUnavailableRequest(@Size(max = 100) String reason) {}
