"use strict";

const elements = Object.fromEntries([
    "conversation-id", "conversation-error", "new-chat", "check-health", "health-dot", "health-status",
    "chat-form", "message", "send", "chat-status", "messages", "welcome", "chat-scroll",
    "document-form", "document-title", "document-content", "add-document", "document-status",
    "document-upload-form", "document-file", "upload-document", "upload-status"
].map(id => [id, document.getElementById(id)]));

let chatPending = false;
let documentPending = false;
let conversationId = readConversationId();
elements["conversation-id"].value = conversationId;

function newConversationId() {
    return "chat-" + crypto.randomUUID();
}

function readConversationId() {
    try {
        const saved = sessionStorage.getItem("enterprise-conversation-id");
        if (saved && /^[A-Za-z0-9_-]{1,100}$/.test(saved)) return saved;
    } catch { /* The interface also works when browser storage is disabled. */ }
    return newConversationId();
}

function saveConversationId() {
    try { sessionStorage.setItem("enterprise-conversation-id", conversationId); } catch { /* Optional storage. */ }
}

function updateConversation() {
    const input = elements["conversation-id"];
    input.value = input.value.trim();
    if (!input.checkValidity()) {
        elements["conversation-error"].textContent = "Use 1–100 letters, numbers, hyphens, or underscores.";
        input.reportValidity();
        return false;
    }
    elements["conversation-error"].textContent = "";
    if (input.value !== conversationId) {
        conversationId = input.value;
        saveConversationId();
        elements.messages.replaceChildren();
        elements.welcome.hidden = false;
        setStatus("chat-status", "Conversation switched. Recent context for this ID is kept by the server.");
    }
    return true;
}

function setStatus(id, text, error = false) {
    elements[id].textContent = text;
    elements[id].classList.toggle("error", error);
}

function addMessage(role, text) {
    elements.welcome.hidden = true;
    const article = document.createElement("article");
    article.className = "message " + role;
    const label = document.createElement("div");
    label.className = "message-label";
    label.textContent = role === "user" ? "YOU" : "✧  ENTERPRISE ASSISTANT";
    const body = document.createElement("div");
    body.className = "message-body";
    // Treat all user/model text as text, never executable HTML or Markdown.
    body.textContent = text;
    article.append(label, body);
    elements.messages.append(article);
    elements["chat-scroll"].scrollTop = elements["chat-scroll"].scrollHeight;
}

async function requestJson(path, body, timeout = 120000) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
        const isFormData = body instanceof FormData;
        const response = await fetch(path, {
            method: body === undefined ? "GET" : "POST",
            headers: body === undefined || isFormData ? { Accept: "application/json" } : {
                Accept: "application/json", "Content-Type": "application/json"
            },
            body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
            signal: controller.signal
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            const fields = Object.values(data.fieldErrors || {}).join("; ");
            throw new Error((data.message || "Request failed (HTTP " + response.status + ").")
                    + (fields ? " " + fields : ""));
        }
        return data;
    } catch (error) {
        if (error.name === "AbortError") throw new Error("The request timed out. It may still finish on the server; check before retrying.");
        if (error instanceof TypeError) throw new Error("Cannot reach the application. Check that Spring Boot is running, then try again.");
        throw error;
    } finally {
        clearTimeout(timer);
    }
}

function setChatPending(pending) {
    chatPending = pending;
    for (const id of ["send", "message", "new-chat", "conversation-id"]) elements[id].disabled = pending;
    document.querySelectorAll("[data-prompt]").forEach(button => { button.disabled = pending; });
    elements["chat-form"].setAttribute("aria-busy", String(pending));
}

elements["chat-form"].addEventListener("submit", async event => {
    event.preventDefault();
    if (chatPending || !updateConversation()) return;
    const message = elements.message.value.trim();
    if (!message) {
        setStatus("chat-status", "Enter a question first.", true);
        elements.message.focus();
        return;
    }
    addMessage("user", message);
    elements.message.value = "";
    setChatPending(true);
    setStatus("chat-status", "Looking into your question…");
    try {
        const response = await requestJson("/api/chat", { conversationId, message });
        if (typeof response.answer !== "string" || !response.answer.trim()) throw new Error("The server returned an empty answer.");
        addMessage("assistant", response.answer);
        setStatus("chat-status", "");
    } catch (error) {
        setStatus("chat-status", error.message, true);
        elements.message.value = message;
    } finally {
        setChatPending(false);
        elements.message.focus();
    }
});

elements.message.addEventListener("keydown", event => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        elements["chat-form"].requestSubmit();
    }
});

elements["conversation-id"].addEventListener("change", updateConversation);
elements["new-chat"].addEventListener("click", () => {
    if (chatPending) return;
    elements["conversation-id"].value = newConversationId();
    updateConversation();
    setStatus("chat-status", "New conversation started.");
    elements.message.value = "";
    elements.message.focus();
});

document.querySelectorAll("[data-prompt]").forEach(button => button.addEventListener("click", () => {
    elements.message.value = button.dataset.prompt;
    elements.message.focus();
}));

elements["document-form"].addEventListener("submit", async event => {
    event.preventDefault();
    if (documentPending) return;
    const title = elements["document-title"].value.trim();
    const content = elements["document-content"].value.trim();
    if (!title || !content) {
        setStatus("document-status", "Enter both a title and document content.", true);
        return;
    }
    documentPending = true;
    for (const id of ["document-title", "document-content", "add-document"]) elements[id].disabled = true;
    elements["document-form"].setAttribute("aria-busy", "true");
    setStatus("document-status", "Adding your document to company knowledge…");
    try {
        const response = await requestJson("/api/documents", { title, content });
        setStatus("document-status", "Added “" + response.title + "” · " + response.chunkCount
                + (response.chunkCount === 1 ? " chunk" : " chunks") + ". You can ask about it now.");
        elements["document-form"].reset();
    } catch (error) {
        setStatus("document-status", error.message, true);
    } finally {
        documentPending = false;
        for (const id of ["document-title", "document-content", "add-document"]) elements[id].disabled = false;
        elements["document-form"].setAttribute("aria-busy", "false");
    }
});

elements["document-file"].addEventListener("change", () => {
    const file = elements["document-file"].files[0];
    elements["upload-document"].disabled = !file;
    setStatus("upload-status", file ? "Selected: " + file.name : "");
});

elements["document-upload-form"].addEventListener("submit", async event => {
    event.preventDefault();
    if (documentPending) return;
    const file = elements["document-file"].files[0];
    if (!file) {
        setStatus("upload-status", "Choose a PDF or TXT file first.", true);
        return;
    }
    if (file.size > 10 * 1024 * 1024) {
        setStatus("upload-status", "The file exceeds the 10 MB upload limit.", true);
        return;
    }
    documentPending = true;
    elements["document-file"].disabled = true;
    elements["upload-document"].disabled = true;
    elements["upload-document"].textContent = "Uploading…";
    elements["document-upload-form"].setAttribute("aria-busy", "true");
    setStatus("upload-status", "Extracting text and adding it to company knowledge…");
    try {
        const form = new FormData();
        form.append("file", file);
        const response = await requestJson("/api/documents/upload", form);
        setStatus("upload-status", "Added “" + response.filename + "” · " + response.chunkCount
                + (response.chunkCount === 1 ? " chunk" : " chunks") + ". You can ask about it now.");
        elements["document-upload-form"].reset();
    } catch (error) {
        setStatus("upload-status", error.message, true);
    } finally {
        documentPending = false;
        elements["document-file"].disabled = false;
        elements["upload-document"].disabled = !elements["document-file"].files.length;
        elements["upload-document"].textContent = "Upload document";
        elements["document-upload-form"].setAttribute("aria-busy", "false");
    }
});

async function checkHealth() {
    elements["check-health"].disabled = true;
    elements["health-status"].textContent = "Checking connection…";
    elements["health-dot"].className = "status-dot";
    try {
        const health = await requestJson("/actuator/health", undefined, 5000);
        if (health.status !== "UP") throw new Error("Unavailable");
        elements["health-status"].textContent = "Application connected";
        elements["health-dot"].classList.add("online");
    } catch {
        elements["health-status"].textContent = "Unavailable · retry";
        elements["health-dot"].classList.add("offline");
    } finally {
        elements["check-health"].disabled = false;
    }
}

elements["check-health"].addEventListener("click", checkHealth);
saveConversationId();
checkHealth();
