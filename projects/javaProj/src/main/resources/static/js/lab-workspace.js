document.addEventListener('DOMContentLoaded', () => {
  const body = document.body;
  const labId = body.dataset.labId;
  const secure = body.dataset.secure === 'true';
  const cameraEnabled = body.dataset.camera === 'true';
  let canEdit = body.dataset.editable === 'true';
  const needsFullscreenStart = secure && canEdit;
  let locked = !canEdit || needsFullscreenStart;
  let dirty = false;
  const editorElement = document.querySelector('#code-editor');
  const runButton = document.querySelector('#run-button');
  const submitButton = document.querySelector('#submit-button');
  const fullscreenButton = document.querySelector('#fullscreen-button');
  const state = document.querySelector('#save-state');
  const savedLabel = document.querySelector('#saved-label');
  const errorBox = document.querySelector('#workspace-error');
  const successBox = document.querySelector('#workspace-success');
  const output = document.querySelector('#stdout');
  const stderr = document.querySelector('#stderr');
  const runMeta = document.querySelector('#run-meta');
  const stdin = document.querySelector('#stdin');
  const flagDisplay = document.querySelector('#flag-count');
  const csrf = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  let editor;
  let timer;
  let cameraStream;
  let cameraTimer;
  let cameraStarting = false;
  let cameraFailed = false;
  let cameraPermissionResolved = false;
  let intentionalCameraStop = false;
  let pendingCameraFailure = null;
  const cameraStatus = document.querySelector('#camera-state');
  const cameraPermissionButton = document.querySelector('#camera-permission-button');
  const retryCameraButton = document.querySelector('#retry-camera-button');
  let cameraVideo;

  if (needsFullscreenStart && cameraEnabled && fullscreenButton) {
    fullscreenButton.disabled = true;
    cameraStatus.textContent = 'Allow camera access before entering full screen.';
  }

  const headers = { 'Content-Type': 'application/json', [csrfHeader || 'X-CSRF-TOKEN']: csrf || '' };
  const source = () => editor.getValue();
  const notice = (element, message) => { element.textContent = message; element.hidden = false; };
  const clearNotices = () => { errorBox.hidden = true; successBox.hidden = true; };

  if (window.CodeMirror) {
    editor = CodeMirror.fromTextArea(editorElement, {
      mode: 'text/x-java', theme: 'eclipse', lineNumbers: true,
      indentUnit: 4, tabSize: 4, matchBrackets: true, viewportMargin: Infinity
    });
  } else {
    editorElement.classList.add('plain-editor');
    editor = {
      getValue: () => editorElement.value,
      setValue: value => { editorElement.value = value; },
      on: (event, callback) => editorElement.addEventListener('input', callback),
      setOption: (key, value) => { if (key === 'readOnly') editorElement.readOnly = value; }
    };
  }
  if (editorElement.value) editor.setValue(editorElement.value);
  editor.setOption('readOnly', locked);
  runButton.disabled = locked;
  submitButton.disabled = locked;

  function lockEditor(message) {
    if (!canEdit) return;
    locked = true;
    canEdit = false;
    dirty = false;
    clearInterval(timer);
    editor.setOption('readOnly', true);
    runButton.disabled = true;
    submitButton.disabled = true;
    state.textContent = 'Read only';
    savedLabel.textContent = message;
    stopCamera();
  }

  async function saveDraft(force = false) {
    if (locked || (!dirty && !force)) return true;
    state.textContent = 'Saving…';
    try {
      const response = await fetch(`/api/student/labs/${labId}/draft`, {
        method: 'PUT', credentials: 'same-origin', headers,
        body: JSON.stringify({ sourceCode: source() })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.message || 'Draft save failed.');
      dirty = false;
      state.textContent = 'Saved';
      savedLabel.textContent = 'Draft saved';
      return true;
    } catch (error) {
      state.textContent = 'Save failed';
      notice(errorBox, `${error.message} Your text remains on this page; edit to retry.`);
      return false;
    }
  }

  if (canEdit) {
    editor.on('change', () => {
      clearNotices();
      dirty = true;
      state.textContent = 'Unsaved changes';
      savedLabel.textContent = 'Saved locally; next sync within 1 minute';
    });
    timer = setInterval(() => saveDraft(), 60_000);
  } else {
    state.textContent = 'Read only';
    savedLabel.textContent = 'Submitted or closed';
  }
  if (needsFullscreenStart) {
    state.textContent = 'Enter full screen to start';
    savedLabel.textContent = 'Secure session not started';
  }

  async function run() {
    clearNotices();
    if (!(await saveDraft(true))) return;
    runButton.disabled = true;
    runMeta.textContent = 'Running…';
    output.textContent = '';
    stderr.hidden = true;
    try {
      const response = await fetch(`/api/student/labs/${labId}/runs`, {
        method: 'POST', credentials: 'same-origin', headers,
        body: JSON.stringify({ sourceCode: source(), standardInput: stdin.value })
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message || 'Run request failed.');
      output.textContent = result.standardOutput || '(no standard output)';
      stderr.textContent = result.standardError || '';
      stderr.hidden = !result.standardError;
      runMeta.textContent = result.timedOut ? `Timed out · ${result.durationMs} ms` : `Exit ${result.exitCode} · ${result.durationMs} ms`;
    } catch (error) {
      runMeta.textContent = 'Run failed';
      notice(errorBox, error.message);
    } finally {
      runButton.disabled = locked;
    }
  }

  async function submit(auto = false) {
    clearNotices();
    if (!auto && !window.confirm('Submit your current Java solution? You will not be able to edit it afterward.')) return;
    submitButton.disabled = true;
    runButton.disabled = true;
    try {
      const response = await fetch(`/api/student/labs/${labId}/submit`, {
        method: 'POST', credentials: 'same-origin', headers,
        body: JSON.stringify({ sourceCode: source() })
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message || 'Submission failed.');
      lockEditor(auto ? 'Auto-submitted' : 'Submitted');
      notice(successBox, auto ? 'Your code was automatically submitted and is now read only.' : 'Your solution was submitted successfully.');
    } catch (error) {
      notice(errorBox, error.message);
      submitButton.disabled = locked;
      runButton.disabled = locked;
    }
  }

  async function sendEvent(type) {
    if (dirty) await saveDraft(true);
    const eventId = crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
      const value = Math.random() * 16 | 0;
      return (char === 'x' ? value : (value & 0x3 | 0x8)).toString(16);
    });
    try {
      const response = await fetch(`/api/student/labs/${labId}/events`, {
        method: 'POST', credentials: 'same-origin', keepalive: true, headers,
        body: JSON.stringify({ eventType: type, eventId })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) return null;
      if (result.flagNumber != null && flagDisplay) flagDisplay.textContent = `Secure flags: ${result.flagNumber} / 4`;
      if (result.autoSubmitted || result.locked) {
        lockEditor('Auto-submitted after secure-session flags');
        if (result.autoSubmitted) notice(successBox, 'The fourth secure-session flag was recorded. Your saved code was automatically submitted and is now read only.');
      }
      return result;
    } catch (_) {
      if (state) state.textContent = 'Integrity event could not sync';
      return null;
    }
  }

  async function startSecureSession() {
    if (!needsFullscreenStart || !canEdit || !locked || !document.fullscreenElement) return;
    const result = await sendEvent('SESSION_STARTED');
    if (!result || result.locked) {
      notice(errorBox, 'The secure session could not start. Keep this page open and enter full screen again.');
      return;
    }
    locked = false;
    editor.setOption('readOnly', false);
    runButton.disabled = false;
    submitButton.disabled = false;
    state.textContent = 'Secure session active';
    savedLabel.textContent = 'Draft will sync every minute';
    if (pendingCameraFailure) {
      await reportCameraUnavailable(pendingCameraFailure);
      pendingCameraFailure = null;
    } else startCamera();
  }

  async function reportCameraUnavailable(reason) {
    if (!cameraStatus) return;
    cameraFailed = true;
    cameraStatus.textContent = 'Camera unavailable · reporting to teacher…';
    try {
      const response = await fetch(`/api/student/labs/${labId}/monitoring/unavailable`, {
        method: 'POST', credentials: 'same-origin', headers,
        body: JSON.stringify({ reason })
      });
      cameraStatus.textContent = response.ok
        ? 'Camera unavailable · reported to teacher'
        : 'Camera unavailable · report to teacher could not be confirmed';
    } catch (_) {
      cameraStatus.textContent = 'Camera unavailable · report to teacher could not be sent';
    }
    if (retryCameraButton) retryCameraButton.hidden = false;
  }

  function stopCamera() {
    clearInterval(cameraTimer);
    cameraTimer = undefined;
    intentionalCameraStop = true;
    if (cameraStream) cameraStream.getTracks().forEach(track => track.stop());
    cameraStream = undefined;
    if (cameraVideo) cameraVideo.remove();
    cameraVideo = undefined;
    window.setTimeout(() => { intentionalCameraStop = false; }, 0);
  }

  async function captureFrame() {
    if (!cameraStream || locked || document.hidden || !cameraVideo?.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = cameraVideo.videoWidth;
    canvas.height = cameraVideo.videoHeight;
    canvas.getContext('2d').drawImage(cameraVideo, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.78));
    if (!blob) {
      await reportCameraUnavailable('frame-capture-failed');
      stopCamera();
      return;
    }
    const form = new FormData();
    form.append('frame', blob, 'frame.jpg');
    const cameraHeaders = { [csrfHeader || 'X-CSRF-TOKEN']: csrf || '' };
    try {
      const response = await fetch(`/api/student/labs/${labId}/monitoring/frames`, {
        method: 'POST', credentials: 'same-origin', headers: cameraHeaders, body: form
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        if (response.status === 429) {
          cameraStatus.textContent = 'Camera check-in is cooling down · the next still will retry shortly';
          clearInterval(cameraTimer);
          cameraTimer = setTimeout(captureFrame, 10_000);
          return;
        }
        cameraFailed = true;
        cameraStatus.textContent = response.status === 503
          ? 'Camera analysis unavailable · report recorded for teacher'
          : 'Camera check-in failed · monitoring is unavailable';
        if (retryCameraButton) retryCameraButton.hidden = false;
        stopCamera();
        return;
      }
      cameraFailed = false;
      if (retryCameraButton) retryCameraButton.hidden = true;
      const indicator = result.phoneDetected || result.multiplePeople
        ? ' · review indicator detected'
        : '';
      cameraStatus.textContent = `Camera check-in active · last still ${new Date(result.timestamp).toLocaleTimeString()}${indicator}`;
      clearTimeout(cameraTimer);
      cameraTimer = setTimeout(captureFrame, 120_000);
    } catch (_) {
      await reportCameraUnavailable('frame-upload-failed');
      stopCamera();
    }
  }

  async function requestCameraPermission() {
    if (!cameraEnabled || !cameraStatus || cameraStarting || cameraStream) return !!cameraStream;
    if (!navigator.mediaDevices?.getUserMedia) {
      pendingCameraFailure = 'camera-api-unavailable';
      cameraFailed = true;
      cameraPermissionResolved = true;
      cameraStatus.textContent = 'Camera is unavailable · continue to full screen and your teacher will be informed';
      return false;
    }
    cameraStarting = true;
    cameraStatus.textContent = 'Waiting for camera permission…';
    try {
      cameraStream = await navigator.mediaDevices.getUserMedia({ video: { width: { ideal: 640 }, height: { ideal: 480 } }, audio: false });
      intentionalCameraStop = false;
      cameraPermissionResolved = true;
      cameraFailed = false;
      cameraStatus.textContent = locked ? 'Camera ready · enter full screen to start the session' : 'Camera permission granted · starting check-in…';
      if (fullscreenButton) fullscreenButton.disabled = false;
      if (retryCameraButton) retryCameraButton.hidden = true;
      cameraStream.getVideoTracks().forEach(track => track.addEventListener('ended', () => {
        if (!intentionalCameraStop && !locked) reportCameraUnavailable('camera-disconnected');
      }, { once: true }));
      return true;
    } catch (error) {
      cameraPermissionResolved = true;
      cameraFailed = true;
      pendingCameraFailure = error.name === 'NotAllowedError' ? 'permission-denied' : 'camera-start-failed';
      cameraStatus.textContent = 'Camera permission was not granted · continue to full screen; your teacher will be informed';
      if (fullscreenButton) fullscreenButton.disabled = false;
      if (retryCameraButton) retryCameraButton.hidden = false;
      return false;
    } finally {
      cameraStarting = false;
    }
  }

  async function startCamera() {
    if (!cameraEnabled || !cameraStatus || locked || document.hidden || cameraStarting || cameraFailed) return;
    if (!cameraStream) {
      cameraStatus.textContent = 'Camera paused · select Retry camera check-in to resume';
      if (retryCameraButton) retryCameraButton.hidden = false;
      return;
    }
    cameraStarting = true;
    try {
      cameraVideo = document.createElement('video');
      cameraVideo.muted = true;
      cameraVideo.playsInline = true;
      cameraVideo.srcObject = cameraStream;
      await cameraVideo.play();
      cameraStatus.textContent = 'Camera permission granted · processing first still…';
      await captureFrame();
    } catch (error) {
      stopCamera();
      await reportCameraUnavailable(error.name === 'NotAllowedError' ? 'permission-denied' : 'camera-start-failed');
    } finally {
      cameraStarting = false;
    }
  }

  async function checkSession() {
    try {
      const response = await fetch(`/api/student/labs/${labId}/status`, { credentials: 'same-origin' });
      if (!response.ok) return;
      const session = await response.json();
      if (flagDisplay && session.secureMode) flagDisplay.textContent = `Secure flags: ${session.flagCount} / 4`;
      if (!session.editable && !locked) {
        lockEditor(session.status === 'ACTIVE' ? 'Submitted' : 'Lab closed');
        notice(successBox, session.status === 'ACTIVE' ? 'This attempt is submitted and is now read only.' : 'The teacher closed this lab. Your saved code is read only.');
      }
    } catch (_) { /* Keep the editor state; a later poll can reconnect. */ }
  }

  runButton.addEventListener('click', run);
  submitButton.addEventListener('click', () => submit(false));
  if (cameraPermissionButton) cameraPermissionButton.addEventListener('click', requestCameraPermission);
  if (fullscreenButton) fullscreenButton.addEventListener('click', async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await document.documentElement.requestFullscreen();
    } catch (_) {
      notice(errorBox, 'Full screen could not start. Use the browser full-screen control and keep this page visible.');
    }
  });
  if (retryCameraButton) retryCameraButton.addEventListener('click', () => {
    cameraFailed = false;
    retryCameraButton.hidden = true;
    requestCameraPermission().then(granted => {
      if (granted && !locked) startCamera();
    });
  });
  document.addEventListener('fullscreenchange', () => {
    if (fullscreenButton) fullscreenButton.textContent = document.fullscreenElement ? 'Exit full screen' : 'Enter full screen';
    if (document.fullscreenElement) startSecureSession();
    if (secure && !document.fullscreenElement) window.setTimeout(() => {
      if (!document.hidden && !document.fullscreenElement) sendEvent('FULLSCREEN_EXIT');
    }, 200);
  });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      sendEvent('TAB_SWITCH');
      stopCamera();
      if (cameraStatus && cameraEnabled && !locked) cameraStatus.textContent = 'Camera paused while page is hidden';
    } else {
      if (dirty) saveDraft();
      if (cameraStream) startCamera();
      else if (cameraEnabled && !locked) {
        cameraStatus.textContent = 'Camera paused · select Retry camera check-in to resume';
        if (retryCameraButton) retryCameraButton.hidden = false;
      }
    }
  });
  window.addEventListener('pagehide', stopCamera);
  const sessionTimer = window.setInterval(checkSession, 10_000);
  window.addEventListener('pagehide', () => clearInterval(sessionTimer), { once: true });

  const editorSurface = editorElement.closest('.CodeMirror') || editorElement;
  const isEditorTarget = target => target === editorElement || editorSurface.contains(target);
  for (const eventName of ['copy', 'cut', 'paste']) document.addEventListener(eventName, eventObject => {
    if (!isEditorTarget(eventObject.target)) return;
    eventObject.preventDefault();
    eventObject.stopPropagation();
    notice(errorBox, `${eventName[0].toUpperCase()}${eventName.slice(1)} is disabled in the lab editor.`);
    sendEvent(eventName.toUpperCase());
  }, true);
  document.addEventListener('keydown', event => {
    if (!isEditorTarget(event.target)) return;
    if ((event.ctrlKey || event.metaKey) && ['c', 'x', 'v'].includes(event.key.toLowerCase())) {
      event.preventDefault();
      event.stopPropagation();
      notice(errorBox, 'Copy, cut, and paste are disabled in the lab editor.');
    }
  }, true);
  startCamera();
});
