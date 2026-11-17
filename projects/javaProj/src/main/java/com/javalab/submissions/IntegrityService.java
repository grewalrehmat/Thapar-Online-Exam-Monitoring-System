package com.javalab.submissions;

import com.javalab.account.UserAccount;
import com.javalab.labs.LabService;
import com.javalab.labs.LabSession;
import com.javalab.labs.LabStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class IntegrityService {
    private static final List<IntegrityEventType> VIOLATIONS = List.of(
            IntegrityEventType.TAB_SWITCH, IntegrityEventType.FULLSCREEN_EXIT);
    private static final DateTimeFormatter TEACHER_TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final LabService labs;
    private final SubmissionRepository submissions;
    private final IntegrityEventRepository events;

    public IntegrityService(LabService labs, SubmissionRepository submissions, IntegrityEventRepository events) {
        this.labs = labs;
        this.submissions = submissions;
        this.events = events;
    }

    @Transactional
    public IntegrityEventView recordBrowserEvent(UserAccount student, Long labId, BrowserEventRequest request) {
        LabSession lab = labs.requireStudentAccess(student, labId);
        if (lab.getStatus() != LabStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "This lab is not open.");
        IntegrityEventType type = IntegrityEventType.valueOf(request.eventType());
        var duplicate = events.findByStudentIdAndLabIdAndIdempotencyKey(student.getId(), labId, request.eventId());
        if (duplicate.isPresent()) {
            IntegrityEvent event = duplicate.get();
            Submission prior = event.getSubmission();
            return new IntegrityEventView(event.getFlagNumber(), event.getFlagNumber() != null && event.getFlagNumber() >= 4,
                    prior != null && prior.getStatus() != SubmissionStatus.DRAFT, event.getOccurredAt());
        }

        if (type == IntegrityEventType.SESSION_STARTED) {
            Submission startedSubmission = submissions.lockByStudentIdAndLabId(student.getId(), labId)
                    .orElseGet(() -> labs.getOrCreateDraft(student, lab));
            IntegrityEvent started = events.save(new IntegrityEvent(lab, student, startedSubmission, type, request.eventId(), null));
            return new IntegrityEventView(null, false, startedSubmission.getStatus() != SubmissionStatus.DRAFT, started.getOccurredAt());
        }

        if (lab.isSecureMode() && !events.existsByStudentIdAndLabIdAndType(student.getId(), labId, IntegrityEventType.SESSION_STARTED)) {
            Submission existing = submissions.findByStudentIdAndLabId(student.getId(), labId).orElse(null);
            IntegrityEvent event = events.save(new IntegrityEvent(lab, student, existing, type, request.eventId(), null));
            return new IntegrityEventView(null, false, existing != null && existing.getStatus() != SubmissionStatus.DRAFT, event.getOccurredAt());
        }

        Submission submission = submissions.lockByStudentIdAndLabId(student.getId(), labId)
                .orElseGet(() -> labs.getOrCreateDraft(student, lab));
        Integer flagNumber = null;
        if (lab.isSecureMode() && VIOLATIONS.contains(type) && submission.getStatus() == SubmissionStatus.DRAFT)
            flagNumber = (int) events.countBySubmissionIdAndTypeInAndFlagNumberIsNotNull(submission.getId(), VIOLATIONS) + 1;
        IntegrityEvent event = events.save(new IntegrityEvent(lab, student, submission, type, request.eventId(), flagNumber));

        boolean autoSubmitted = false;
        if (flagNumber != null && flagNumber >= 4 && submission.getStatus() == SubmissionStatus.DRAFT) {
            submission.submit(submission.getSourceCode());
            events.save(new IntegrityEvent(lab, student, submission, IntegrityEventType.AUTO_SUBMIT,
                    UUID.randomUUID().toString(), flagNumber));
            autoSubmitted = true;
        }
        return new IntegrityEventView(flagNumber, autoSubmitted, submission.getStatus() != SubmissionStatus.DRAFT, event.getOccurredAt());
    }

    @Transactional
    public void record(UserAccount student, LabSession lab, Submission submission, IntegrityEventType type) {
        events.save(new IntegrityEvent(lab, student, submission, type, UUID.randomUUID().toString(), null));
    }

    @Transactional
    public void record(UserAccount student, LabSession lab, Submission submission, IntegrityEventType type, String details) {
        events.save(new IntegrityEvent(lab, student, submission, type, UUID.randomUUID().toString(), null, details));
    }

    @Transactional(readOnly = true)
    public List<TeacherActivityView> activityForStudent(UserAccount teacher, Long labId, Long studentId) {
        labs.requireTeacherStudentAccess(teacher, labId, studentId);
        return events.findByLabIdAndStudentIdOrderByOccurredAtAscIdAsc(labId, studentId).stream()
                .map(this::present)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IntegrityEvent> allForLab(UserAccount teacher, Long labId) {
        labs.requireTeacherAccess(teacher, labId);
        return events.findByLabIdOrderByOccurredAtAscIdAsc(labId);
    }

    @Transactional(readOnly = true)
    public long flagCount(UserAccount student, LabSession lab) {
        return lab.isSecureMode() ? events.countByStudentIdAndLabIdAndTypeInAndFlagNumberIsNotNull(student.getId(), lab.getId(), VIOLATIONS) : 0;
    }

    @Transactional(readOnly = true)
    public boolean hasSecureSessionStarted(UserAccount student, Long labId) {
        return events.existsByStudentIdAndLabIdAndType(student.getId(), labId, IntegrityEventType.SESSION_STARTED);
    }

    @Transactional(readOnly = true)
    public java.time.Instant latestCameraEvent(UserAccount student, Long labId) {
        return events.findTopByStudentIdAndLabIdAndTypeInOrderByOccurredAtDesc(student.getId(), labId,
                List.of(IntegrityEventType.CAMERA_CAPTURE, IntegrityEventType.CAMERA_UNAVAILABLE))
                .map(IntegrityEvent::getOccurredAt).orElse(null);
    }

    TeacherActivityView present(IntegrityEvent event) {
        return new TeacherActivityView(TEACHER_TIME.format(event.getOccurredAt()), label(event.getType()), event.getFlagNumber(), event.getDetails());
    }

    private String label(IntegrityEventType type) {
        return switch (type) {
            case TAB_SWITCH -> "Tab switched";
            case FULLSCREEN_EXIT -> "Fullscreen exited";
            case COPY -> "Copy blocked";
            case CUT -> "Cut blocked";
            case PASTE -> "Paste blocked";
            case DRAFT_SAVE -> "Draft saved";
            case RUN -> "Code run";
            case SUBMIT -> "Submitted";
            case AUTO_SUBMIT -> "Auto-submitted";
            case GRADE_UPDATED -> "Grade updated";
            case CAMERA_UNAVAILABLE -> "Camera unavailable";
            case CAMERA_CAPTURE -> "Camera check";
            case SESSION_STARTED -> "Secure session started";
        };
    }
}
