package com.javalab.submissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BrowserEventRequest(
        @NotBlank @Pattern(regexp = "TAB_SWITCH|FULLSCREEN_EXIT|COPY|CUT|PASTE|SESSION_STARTED") String eventType,
        @NotBlank @Size(max = 36) @Pattern(regexp = "[0-9a-fA-F-]{36}") String eventId) {}
