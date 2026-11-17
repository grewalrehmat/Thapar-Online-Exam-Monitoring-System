package com.javalab.submissions;

import java.time.Instant;

public record IntegrityEventView(Integer flagNumber, boolean autoSubmitted, boolean locked, Instant occurredAt) {}
