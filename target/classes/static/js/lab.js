const labId = document.body.dataset.labId;
const secure = document.body.dataset.secure === "true";

const code = document.getElementById("code");
const saveStatus = document.getElementById("saveStatus");
const stdout = document.getElementById("stdout");
const stderr = document.getElementById("stderr");

let saveTimer;

async function post(url, body) {
    try {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(body)
        });

        const text = await response.text();

        let data;

        try {
            data = text ? JSON.parse(text) : {};
        } catch {
            throw new Error(
                `Server returned non-JSON response (${response.status}): ${text}`
            );
        }

        if (!response.ok) {
            throw new Error(
                data.error || `Request failed with HTTP ${response.status}`
            );
        }

        return data;

    } catch (error) {
        console.error("API ERROR:", error);
        throw error;
    }
}


// -------------------------
// AUTO SAVE
// -------------------------

function scheduleSave() {
    saveStatus.textContent = "Saving...";

    clearTimeout(saveTimer);

    saveTimer = setTimeout(async () => {
        try {
            await post(`/api/labs/${labId}/draft`, {
                code: code.value
            });

            saveStatus.textContent = "Saved";

        } catch (error) {
            console.error(error);
            saveStatus.textContent = "Save failed";
        }
    }, 900);
}

code.addEventListener("input", scheduleSave);


// -------------------------
// RUN CODE
// -------------------------

document.getElementById("runBtn").addEventListener("click", async () => {

    stdout.textContent = "Running...";
    stderr.textContent = "";

    try {

        const result = await post(
            `/api/labs/${labId}/run`,
            {
                code: code.value
            }
        );

        console.log("RUN RESULT:", result);

        stdout.textContent =
            result.stdout && result.stdout.trim()
                ? result.stdout
                : "(program produced no output)";

        stderr.textContent =
            result.stderr && result.stderr.trim()
                ? result.stderr
                : "";

        if (result.exitCode !== 0) {
            stderr.textContent +=
                `\n\nProcess exited with code ${result.exitCode}`;
        }

    } catch (error) {

        console.error("RUN FAILED:", error);

        stdout.textContent = "";

        stderr.textContent =
            "Could not run the program.\n\n" +
            error.message;

    }
});


// -------------------------
// SUBMIT
// -------------------------

document.getElementById("submitBtn").addEventListener("click", async () => {

    if (!confirm(
        "Submit your final code?\n\n" +
        "You will not be able to edit it afterwards."
    )) {
        return;
    }

    try {

        const result = await post(
            `/api/labs/${labId}/submit`,
            {
                code: code.value
            }
        );

        if (result.status === "submitted") {
            alert("Submitted successfully.");
            location.reload();
        }

    } catch (error) {

        alert(
            "Submission failed:\n\n" +
            error.message
        );

    }
});


// -------------------------
// INTEGRITY EVENTS
// -------------------------

async function event(type, metadata = null) {

    if (!secure) {
        return;
    }

    try {

        await post(
            `/api/labs/${labId}/activity`,
            {
                type,
                metadata
            }
        );

    } catch (error) {

        console.error(
            "Could not record activity:",
            error
        );

    }
}





document.addEventListener("copy", (e) => {
    if (secure && evaluationStarted) {
        e.preventDefault();
        event("COPY");
        showFlagMessage("Copying is disabled during Evaluation.");
    }
});


document.addEventListener("paste", (e) => {
    if (secure && evaluationStarted) {
        e.preventDefault();
        event("PASTE");
        showFlagMessage("Pasting is disabled during Evaluation.");
    }
});


document.addEventListener("cut", (e) => {
    if (secure && evaluationStarted) {
        e.preventDefault();
        event("CUT");
        showFlagMessage("Cutting is disabled during Evaluation.");
    }
});




/*
 * ============================================================
 * EVALUATION MODE
 * ============================================================
 */

const fullscreenBtn =
    document.getElementById("fullscreenBtn");

const flagMessage =
    document.getElementById("flagMessage");

const flagCounter =
    document.getElementById("flagCounter");

const MAX_FLAGS = 3;

let flags = secure
    ? Number(document.body.dataset.integrityFlags || 0)
    : 0;
let evaluationStarted = false;

// Restore integrity state after page refresh.
if (secure && flags >= MAX_FLAGS) {
    if (flagCounter) {
        flagCounter.textContent = `Flags: ${MAX_FLAGS}/${MAX_FLAGS}`;
    }

    if (code) {
        code.disabled = true;
        code.readOnly = true;
    }

    const runBtn = document.getElementById("runBtn");
    const submitBtn = document.getElementById("submitBtn");

    if (runBtn) runBtn.disabled = true;
    if (submitBtn) submitBtn.disabled = true;

    showFlagMessage(
        "Maximum integrity flags reached. This evaluation is locked. Please contact your instructor.",
        true
    );
}



/*
 * Send an activity event.
 *
 * This is the same mechanism that worked in the old version.
 */
async function recordEvaluationEvent(type, metadata = null) {

    if (!secure) {
        return;
    }

    try {

        await post(
            `/api/labs/${labId}/activity`,
            {
                type: type,
                metadata: metadata
            }
        );

        console.log("Activity recorded:", type);

    } catch (error) {

        console.error(
            "Failed to record activity:",
            error
        );
    }
}


/*
 * Show the flag message.
 */
function showFlagMessage(message) {

    if (!flagMessage) {
        alert(message);
        return;
    }

    flagMessage.textContent = message;
    flagMessage.hidden = false;

    setTimeout(() => {
        flagMessage.hidden = true;
    }, 5000);
}


/*
 * Raise a flag.
 */
async function raiseFlag(type, reason) {
    if (!secure || !evaluationStarted) {
        return;
    }

    // Once the maximum is reached, NEVER raise another flag.
    if (flags >= MAX_FLAGS) {
        return;
    }

    flags++;

    if (flagCounter) {
        flagCounter.textContent = `Flags: ${flags}/${MAX_FLAGS}`;
    }

    await recordEvaluationEvent(type, reason);

    if (flags < MAX_FLAGS) {
        showFlagMessage(
            `${reason} Flag ${flags}/${MAX_FLAGS} raised.`
        );
        return;
    }

    // =========================================================
    // 3/3 — LOCK THE EVALUATION
    // =========================================================

    showFlagMessage(
        "Maximum integrity flags reached. Your evaluation has been locked. Please contact your instructor.",
        true
    );

    if (code) {
        code.disabled = true;
        code.readOnly = true;
    }

    const runBtn = document.getElementById("runBtn");
    const submitBtn = document.getElementById("submitBtn");

    if (runBtn) {
        runBtn.disabled = true;
    }

    if (submitBtn) {
        submitBtn.disabled = true;
    }

    if (fullscreenBtn) {
        fullscreenBtn.disabled = true;
    }

    evaluationStarted = false;
}
if (fullscreenBtn) {

    fullscreenBtn.addEventListener("click", async () => {

        try {

            await document.documentElement.requestFullscreen();

            evaluationStarted = true;

            fullscreenBtn.textContent =
                "Evaluation Started";

            fullscreenBtn.disabled = true;

            if (code) {
                code.disabled = false;
                code.focus();
            }

            console.log(
                "Evaluation started. Fullscreen:",
                !!document.fullscreenElement
            );

        } catch (error) {

            console.error(
                "Fullscreen failed:",
                error
            );

            alert(
                "Fullscreen permission was not granted. " +
                "Please try again."
            );
        }
    });
}


/*
 * TAB SWITCH
 *
 * This is deliberately the same basic mechanism
 * from the old working version.
 */
let lastIntegrityEvent = 0;

function handlePossibleTabSwitch(reason) {
    if (!secure || !evaluationStarted) {
        return;
    }

    // Prevent visibilitychange + blur from creating two flags
    // for the exact same switch.
    const now = Date.now();

    if (now - lastIntegrityEvent < 1000) {
        return;
    }

    lastIntegrityEvent = now;

    console.log("TAB SWITCH DETECTED:", reason);

    raiseFlag(
        "TAB_SWITCH",
        "Tab switched, please refrain from doing that."
    );
}



/*
 * FULLSCREEN EXIT
 *
 * Switching browser tabs can make the browser exit fullscreen
 * automatically. That should count as TAB_SWITCH only, not as
 * a second FULLSCREEN_EXIT flag.
 */
document.addEventListener("fullscreenchange", () => {

    console.log(
        "fullscreenchange:",
        !!document.fullscreenElement,
        "hidden:",
        document.hidden
    );

    if (
        secure &&
        evaluationStarted &&
        !document.fullscreenElement &&
        !document.hidden
    ) {

        raiseFlag(
            "FULLSCREEN_EXIT",
            "You exited fullscreen."
        );
    }
});



/*
 * Evaluation starts locked.
 */
if (secure && code) {
    code.disabled = true;
}



// ============================================================
// TAB SWITCH DETECTION
// ============================================================

document.addEventListener("visibilitychange", () => {

    console.log(
        "visibilitychange:",
        document.visibilityState
    );

    if (!secure || !evaluationStarted) {
        return;
    }

    if (document.visibilityState === "hidden") {

        console.log("TAB SWITCH DETECTED");

        raiseFlag(
            "TAB_SWITCH",
            "Tab switched, please refrain from doing that."
        );
    }
});


