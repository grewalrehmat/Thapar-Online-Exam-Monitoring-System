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


document.addEventListener("visibilitychange", () => {

    if (document.hidden) {
        event("TAB_SWITCH");
    }

});


document.addEventListener("copy", () => {
    event("COPY");
});


document.addEventListener("paste", () => {
    event("PASTE");
});


document.addEventListener("cut", () => {
    event("CUT");
});


document.addEventListener("fullscreenchange", () => {

    if (!document.fullscreenElement) {
        event("FULLSCREEN_EXIT");
    }

});
