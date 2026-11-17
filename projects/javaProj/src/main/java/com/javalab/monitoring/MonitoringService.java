package com.javalab.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javalab.account.UserAccount;
import com.javalab.labs.LabService;
import com.javalab.labs.LabSession;
import com.javalab.labs.LabStatus;
import com.javalab.submissions.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Service
public class MonitoringService {
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
    private final LabService labs;
    private final SubmissionRepository submissions;
    private final MonitoringSnapshotRepository snapshots;
    private final IntegrityService integrity;
    private final RestClient client;
    private final String token;
    private final ObjectMapper mapper;
    private final long retentionHours;

    public MonitoringService(LabService labs, SubmissionRepository submissions, MonitoringSnapshotRepository snapshots,
                             IntegrityService integrity, @Qualifier("monitoringRestClient") RestClient client,
                             Environment environment, ObjectMapper mapper,
                             @Value("${javalab.monitoring.retention-hours:2160}") long retentionHours) {
        this.labs = labs;
        this.submissions = submissions;
        this.snapshots = snapshots;
        this.integrity = integrity;
        this.client = client;
        this.token = environment.getProperty("javalab.monitoring.token", "local-dev-monitoring-token");
        this.mapper = mapper;
        this.retentionHours = Math.max(1, retentionHours);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public MonitoringAnalysis analyze(UserAccount student, Long labId, byte[] frame, String contentType) {
        LabSession lab = eligibleLab(student, labId);
        if (frame == null || frame.length == 0 || frame.length > MAX_FRAME_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Camera frame is empty or exceeds 2 MB.");
        if (!MediaType.IMAGE_JPEG_VALUE.equals(contentType) && !MediaType.IMAGE_PNG_VALUE.equals(contentType))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Camera frame must be JPEG or PNG.");
        Submission submission = submissions.lockByStudentIdAndLabId(student.getId(), labId)
                .orElseGet(() -> labs.getOrCreateDraft(student, lab));
        if (submission != null && submission.getStatus() != SubmissionStatus.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Camera monitoring stopped because this attempt is locked.");
        java.time.Instant latestCaptureEvent = integrity.latestCameraEvent(student, labId);
        if (latestCaptureEvent != null && latestCaptureEvent.plus(120, ChronoUnit.SECONDS).isAfter(java.time.Instant.now()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Camera check-ins are limited to one still image every two minutes.");

        MonitoringAnalysis result;
        try {
            String filename = contentType.equals(MediaType.IMAGE_PNG_VALUE) ? "frame.png" : "frame.jpg";
            ByteArrayResource resource = new ByteArrayResource(frame) {
                @Override public String getFilename() { return filename; }
            };
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType(contentType));
            partHeaders.setContentDispositionFormData("file", filename);
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", new HttpEntity<>(resource, partHeaders));
            result = client.post().uri("/analyze").header("X-Internal-Token", token)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(MonitoringAnalysis.class);
            if (result == null || result.snapshotId() == null || result.timestamp() == null)
                throw new IllegalStateException("Monitoring service returned incomplete data.");
            UUID.fromString(result.snapshotId());
        } catch (RestClientException | IllegalStateException | IllegalArgumentException error) {
            integrity.record(student, lab, submission, IntegrityEventType.CAMERA_UNAVAILABLE, "monitoring-service-unavailable");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Camera analysis is unavailable. The event was recorded for your teacher.", error);
        }
        try {
            String json = mapper.writeValueAsString(result.detections());
            snapshots.save(new MonitoringSnapshot(result.snapshotId(), lab, student, submission, result, json));
            integrity.record(student, lab, submission, IntegrityEventType.CAMERA_CAPTURE, null);
            return result;
        } catch (JsonProcessingException error) {
            integrity.record(student, lab, submission, IntegrityEventType.CAMERA_UNAVAILABLE, "monitoring-result-invalid");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Camera analysis returned invalid data. The event was recorded for your teacher.", error);
        }
    }

    @Transactional
    public void reportUnavailable(UserAccount student, Long labId, String reason) {
        LabSession lab = eligibleLab(student, labId);
        String cleaned = reason == null ? "" : reason.replaceAll("[^A-Za-z0-9_-]", "");
        String safeReason = cleaned.isBlank() ? "camera-unavailable" : cleaned.substring(0, Math.min(80, cleaned.length()));
        Submission submission = submissions.findByStudentIdAndLabId(student.getId(), labId).orElse(null);
        integrity.record(student, lab, submission, IntegrityEventType.CAMERA_UNAVAILABLE, safeReason);
    }

    @Transactional(readOnly = true)
    public List<MonitoringSnapshot> forTeacher(UserAccount teacher, Long labId, Long studentId) {
        labs.requireTeacherStudentAccess(teacher, labId, studentId);
        return snapshots.findByLabIdAndStudentIdOrderByCapturedAtDesc(labId, studentId);
    }

    @Transactional(readOnly = true)
    public byte[] snapshotImage(UserAccount teacher, String snapshotId) {
        MonitoringSnapshot snapshot = snapshots.findBySnapshotId(snapshotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found."));
        labs.requireTeacherStudentAccess(teacher, snapshot.getLab().getId(), snapshot.getStudent().getId());
        try {
            byte[] image = client.get().uri("/snapshots/{id}", snapshotId).header("X-Internal-Token", token)
                    .accept(MediaType.IMAGE_JPEG).retrieve().body(byte[].class);
            if (image == null || image.length == 0) throw new IllegalStateException("Snapshot image is empty.");
            return image;
        } catch (RestClientException | IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Snapshot image is temporarily unavailable.", error);
        }
    }

    @Scheduled(fixedDelayString = "${javalab.monitoring.cleanup-delay-ms:3600000}")
    @Transactional
    public void cleanupOldMetadata() {
        snapshots.deleteByCapturedAtBefore(Instant.now().minus(Duration.ofHours(retentionHours)));
    }

    private LabSession eligibleLab(UserAccount student, Long labId) {
        LabSession lab = labs.requireStudentAccess(student, labId);
        if (lab.getStatus() != LabStatus.ACTIVE || !lab.isSecureMode() || !lab.isCameraMonitoring())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Camera monitoring is not enabled for this active lab.");
        if (!integrity.hasSecureSessionStarted(student, labId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Start the secure session in full screen before camera check-ins.");
        return lab;
    }
}
