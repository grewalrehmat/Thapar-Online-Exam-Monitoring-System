package com.javalab.monitoring;

import com.javalab.account.UserAccount;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MonitoringController {
    private final MonitoringService monitoring;
    public MonitoringController(MonitoringService monitoring) { this.monitoring = monitoring; }

    @PostMapping("/api/student/labs/{labId}/monitoring/frames")
    public MonitoringAnalysis frame(@AuthenticationPrincipal UserAccount student, @PathVariable Long labId,
                                    @RequestPart("frame") MultipartFile frame) throws java.io.IOException {
        return monitoring.analyze(student, labId, frame.getBytes(), frame.getContentType());
    }

    @PostMapping("/api/student/labs/{labId}/monitoring/unavailable")
    public ResponseEntity<Void> unavailable(@AuthenticationPrincipal UserAccount student, @PathVariable Long labId,
                                            @Valid @RequestBody CameraUnavailableRequest request) {
        monitoring.reportUnavailable(student, labId, request.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/teacher/snapshots/{snapshotId}/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal UserAccount teacher, @PathVariable String snapshotId) {
        byte[] data = monitoring.snapshotImage(teacher, snapshotId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(data);
    }
}
