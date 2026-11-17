package com.javalab.monitoring;

import java.time.Instant;
import java.util.List;

public record MonitoringAnalysis(Instant timestamp, String snapshotId, int personCount, int phoneCount,
                                boolean multiplePeople, boolean phoneDetected, List<Detection> detections) {
    public record Detection(int classId, String label, double confidence, List<Double> box) {}
}
