"use strict";

const MAX_MESSAGE_LENGTH = 500;
const els = {
    error: document.getElementById("error"),
    notice: document.getElementById("notice"),
    authView: document.getElementById("auth-view"),
    chatView: document.getElementById("chat-view"),
    accountBar: document.getElementById("account-bar"),
    currentUser: document.getElementById("current-user"),
    status: document.getElementById("status"),
    enableNotifications: document.getElementById("enable-notifications"),
    authTitle: document.getElementById("auth-title"),
    authDescription: document.getElementById("auth-description"),
    authForm: document.getElementById("auth-form"),
    legacyVerificationView: document.getElementById("legacy-verification-view"),
    legacyVerificationForm: document.getElementById("legacy-verification-form"),
    legacyEmail: document.getElementById("legacy-email"),
    legacySendCode: document.getElementById("legacy-send-code"),
    legacyCodeStage: document.getElementById("legacy-code-stage"),
    legacyCode: document.getElementById("legacy-code"),
    legacyResendCode: document.getElementById("legacy-resend-code"),
    legacyVerifySubmit: document.getElementById("legacy-verify-submit"),
    legacyChangeEmail: document.getElementById("legacy-change-email"),
    legacyBack: document.getElementById("legacy-back"),
    legacyVerificationLink: document.getElementById("legacy-verification-link"),
    legacyVerificationLinkContainer: document.getElementById("legacy-verification-link-container"),
    authSwitchContainer: document.getElementById("auth-switch-container"),
    passwordResetView: document.getElementById("password-reset-view"),
    passwordResetForm: document.getElementById("password-reset-form"),
    resetEmail: document.getElementById("reset-email"),
    resetSendCode: document.getElementById("reset-send-code"),
    resetCodeStage: document.getElementById("reset-code-stage"),
    resetCode: document.getElementById("reset-code"),
    resetNewPassword: document.getElementById("reset-new-password"),
    resetConfirmPassword: document.getElementById("reset-confirm-password"),
    resetResendCode: document.getElementById("reset-resend-code"),
    resetChangeEmail: document.getElementById("reset-change-email"),
    resetBack: document.getElementById("reset-back"),
    forgotPasswordLink: document.getElementById("forgot-password-link"),
    forgotPasswordLinkContainer: document.getElementById("forgot-password-link-container"),
    identity: document.getElementById("identity"),
    identityLabel: document.getElementById("identity-label"),
    signupFields: document.getElementById("signup-fields"),
    email: document.getElementById("email"),
    handle: document.getElementById("handle"),
    password: document.getElementById("password"),
    passwordLabel: document.getElementById("password-label"),
    verificationFields: document.getElementById("verification-fields"),
    verificationCode: document.getElementById("verification-code"),
    resendCode: document.getElementById("resend-code"),
    authSubmit: document.getElementById("auth-submit"),
    authSwitchLabel: document.getElementById("auth-switch-label"),
    authSwitch: document.getElementById("auth-switch"),
    logout: document.getElementById("logout-btn"),
    searchForm: document.getElementById("search-form"),
    userSearch: document.getElementById("user-search"),
    searchResults: document.getElementById("search-results"),
    groupForm: document.getElementById("group-form"),
    groupTitle: document.getElementById("group-title"),
    groupMembers: document.getElementById("group-members"),
    conversationList: document.getElementById("conversation-list"),
    refresh: document.getElementById("refresh-btn"),
    noConversation: document.getElementById("no-conversation"),
    panel: document.getElementById("conversation-panel"),
    conversationTitle: document.getElementById("conversation-title"),
    conversationMembers: document.getElementById("conversation-members"),
    manageGroupButton: document.getElementById("manage-group-btn"),
    groupManager: document.getElementById("group-manager"),
    addMemberForm: document.getElementById("add-member-form"),
    newMember: document.getElementById("new-member"),
    memberList: document.getElementById("member-list"),
    loadOlder: document.getElementById("load-older"),
    messages: document.getElementById("messages"),
    messageForm: document.getElementById("message-form"),
    messageInput: document.getElementById("message-input"),
    sendButton: document.getElementById("send-btn"),
    counter: document.getElementById("char-counter"),
};

let csrf = null;
let signingUp = false;
let awaitingVerification = false;
let pendingEmail = null;
let pendingPassword = null;
let currentUser = null;
let conversations = [];
let activeConversation = null;
let activeSubscription = null;
let stompClient = null;
let renderedMessageIds = new Set();
let hasOlderMessages = false;
let searchTimer = null;
let conversationRefreshTimer = null;
let pushRegistration = null;

function showError(message) {
    clearNotice();
    els.error.textContent = message;
    els.error.hidden = false;
}

function showNotice(message) {
    clearError();
    els.notice.textContent = message;
    els.notice.hidden = false;
}

function clearNotice() {
    els.notice.textContent = "";
    els.notice.hidden = true;
}

function clearError() {
    els.error.textContent = "";
    els.error.hidden = true;
}

async function refreshCsrf() {
    const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
    const body = await response.json();
    if (!response.ok) {
        throw new Error(body.message || "Could not initialize a secure session.");
    }
    csrf = body;
}

async function api(path, options) {
    const requestOptions = Object.assign({ credentials: "same-origin" }, options || {});
    requestOptions.headers = Object.assign({}, requestOptions.headers || {});
    if (requestOptions.body && typeof requestOptions.body !== "string") {
        requestOptions.headers["Content-Type"] = "application/json";
        requestOptions.body = JSON.stringify(requestOptions.body);
    }
    if (requestOptions.method && requestOptions.method !== "GET") {
        if (!csrf) {
            await refreshCsrf();
        }
        requestOptions.headers[csrf.headerName] = csrf.token;
    }
    const response = await fetch(path, requestOptions);
    if (response.status === 204) {
        return null;
    }
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
        const error = new Error(body.message || "The request could not be completed.");
        error.status = response.status;
        throw error;
    }
    return body;
}

function setAuthMode(createAccount) {
    signingUp = createAccount;
    awaitingVerification = false;
    pendingEmail = null;
    pendingPassword = null;
    els.authTitle.textContent = createAccount ? "Create your account" : "Welcome back";
    els.authDescription.textContent = createAccount
        ? "Choose a username so people can find you."
        : "Sign in to pick up where your conversations left off.";
    els.signupFields.hidden = !createAccount;
    els.authForm.hidden = false;
    els.legacyVerificationView.hidden = true;
    els.passwordResetView.hidden = true;
    els.authSwitchContainer.hidden = false;
    els.legacyVerificationLinkContainer.hidden = createAccount;
    els.forgotPasswordLinkContainer.hidden = createAccount;
    els.legacyCodeStage.hidden = true;
    els.legacyCode.required = false;
    els.legacyCode.value = "";
    els.legacyEmail.disabled = false;
    els.legacySendCode.hidden = false;
    els.legacyChangeEmail.hidden = true;
    els.resetCodeStage.hidden = true;
    els.resetCode.required = false;
    els.resetNewPassword.required = false;
    els.resetConfirmPassword.required = false;
    els.resetCode.value = "";
    els.resetNewPassword.value = "";
    els.resetConfirmPassword.value = "";
    els.resetEmail.disabled = false;
    els.resetSendCode.hidden = false;
    els.resetChangeEmail.hidden = true;
    els.verificationFields.hidden = true;
    els.verificationCode.required = false;
    els.email.required = createAccount;
    els.handle.required = createAccount;
    els.email.disabled = false;
    els.handle.disabled = false;
    els.identity.hidden = createAccount;
    els.identityLabel.hidden = createAccount;
    els.identity.required = !createAccount;
    els.passwordLabel.hidden = false;
    els.password.hidden = false;
    els.password.required = true;
    els.authSwitchLabel.hidden = false;
    els.authSwitch.hidden = false;
    els.password.autocomplete = createAccount ? "new-password" : "current-password";
    els.authSubmit.textContent = createAccount ? "Create account" : "Sign in";
    els.authSwitchLabel.textContent = createAccount ? "Already have an account?" : "New to ClassChat?";
    els.authSwitch.textContent = createAccount ? "Sign in" : "Create an account";
    clearError();
    clearNotice();
}

function showLegacyVerification() {
    setAuthMode(false);
    els.authForm.hidden = true;
    els.authSwitchContainer.hidden = true;
    els.legacyVerificationLinkContainer.hidden = true;
    els.forgotPasswordLinkContainer.hidden = true;
    els.legacyVerificationView.hidden = false;
    els.authTitle.textContent = "Verify an existing account";
    els.authDescription.textContent = "Enter the email on your account to receive a verification code.";
    clearError();
}

function showPasswordReset() {
    setAuthMode(false);
    els.authForm.hidden = true;
    els.authSwitchContainer.hidden = true;
    els.legacyVerificationLinkContainer.hidden = true;
    els.forgotPasswordLinkContainer.hidden = true;
    els.passwordResetView.hidden = false;
    els.authTitle.textContent = "Reset your password";
    els.authDescription.textContent = "We’ll email a reset code to the address on your verified account.";
    clearError();
}

function returnToSignIn(verifiedEmail) {
    setAuthMode(false);
    if (verifiedEmail) {
        els.identity.value = verifiedEmail;
        showNotice("Email verified. Sign in with your email or @username.");
    }
}

function setVerificationMode(email) {
    awaitingVerification = true;
    pendingEmail = email;
    els.authTitle.textContent = "Verify your email";
    els.authDescription.textContent = "Enter the 6-digit code we sent to " + email + ".";
    els.signupFields.hidden = true;
    els.identity.hidden = true;
    els.identityLabel.hidden = true;
    els.identity.required = false;
    els.passwordLabel.hidden = true;
    els.password.hidden = true;
    els.password.required = false;
    els.verificationFields.hidden = false;
    els.verificationCode.required = true;
    els.authSubmit.textContent = "Verify email";
    els.authSwitchLabel.hidden = true;
    els.authSwitch.hidden = true;
    els.verificationCode.value = "";
    clearError();
    clearNotice();
}

async function submitAuth(event) {
    event.preventDefault();
    clearError();
    try {
        await refreshCsrf();
        if (awaitingVerification) {
            const result = await api("/api/auth/verify-email", {
                method: "POST",
                body: { email: pendingEmail, code: els.verificationCode.value },
            });
            currentUser = await api("/api/auth/login", {
                method: "POST",
                body: { identity: pendingEmail, password: pendingPassword },
            });
            pendingEmail = null;
            pendingPassword = null;
            awaitingVerification = false;
            showNotice(result.message);
            await enterChat();
            return;
        }
        const request = signingUp
            ? {
                email: els.email.value,
                handle: els.handle.value,
                password: els.password.value,
            }
            : {
                identity: els.identity.value,
                password: els.password.value,
            };
        const result = await api(signingUp ? "/api/auth/register" : "/api/auth/login", {
            method: "POST",
            body: request,
        });
        if (signingUp) {
            pendingPassword = request.password;
            setVerificationMode(result.email);
            showNotice(result.message);
            return;
        }
        currentUser = result;
        await enterChat();
    } catch (error) {
        showError(error.message);
    }
}

async function resendVerificationCode() {
    clearError();
    try {
        const result = await api("/api/auth/resend-verification", {
            method: "POST",
            body: { email: pendingEmail },
        });
        showNotice(result.message);
    } catch (error) {
        showError(error.message);
    }
}

async function requestLegacyVerificationCode() {
    clearError();
    if (!els.legacyEmail.reportValidity()) {
        return;
    }
    try {
        const result = await api("/api/auth/resend-verification", {
            method: "POST",
            body: { email: els.legacyEmail.value },
        });
        els.legacyEmail.disabled = true;
        els.legacySendCode.hidden = true;
        els.legacyCodeStage.hidden = false;
        els.legacyCode.required = true;
        els.legacyChangeEmail.hidden = false;
        showNotice(result.message);
    } catch (error) {
        showError(error.message);
    }
}

async function requestPasswordResetCode() {
    clearError();
    if (!els.resetEmail.reportValidity()) {
        return;
    }
    try {
        const result = await api("/api/auth/password-reset/request", {
            method: "POST",
            body: { email: els.resetEmail.value },
        });
        els.resetEmail.disabled = true;
        els.resetSendCode.hidden = true;
        els.resetCodeStage.hidden = false;
        els.resetCode.required = true;
        els.resetNewPassword.required = true;
        els.resetConfirmPassword.required = true;
        els.resetChangeEmail.hidden = false;
        showNotice(result.message);
    } catch (error) {
        showError(error.message);
    }
}

async function submitPasswordReset(event) {
    event.preventDefault();
    clearError();
    if (els.resetNewPassword.value !== els.resetConfirmPassword.value) {
        showError("The new password and confirmation do not match.");
        return;
    }
    try {
        const result = await api("/api/auth/password-reset/confirm", {
            method: "POST",
            body: {
                email: els.resetEmail.value,
                code: els.resetCode.value,
                newPassword: els.resetNewPassword.value,
            },
        });
        const email = els.resetEmail.value;
        setAuthMode(false);
        els.identity.value = email;
        els.password.value = "";
        showNotice(result.message);
    } catch (error) {
        showError(error.message);
    }
}

async function verifyLegacyEmail(event) {
    event.preventDefault();
    clearError();
    try {
        await api("/api/auth/verify-email", {
            method: "POST",
            body: { email: els.legacyEmail.value, code: els.legacyCode.value },
        });
        const verifiedEmail = els.legacyEmail.value;
        returnToSignIn(verifiedEmail);
    } catch (error) {
        showError(error.message);
    }
}

async function enterChat() {
    els.authView.hidden = true;
    els.chatView.hidden = false;
    els.accountBar.hidden = false;
    els.currentUser.textContent = "@" + currentUser.handle;
    setStatus("connecting");
    try {
        await loadConversations();
        await openRequestedConversation();
        connectSocket();
        configureNotifications().catch((error) => showError(error.message));
        if (conversationRefreshTimer === null) {
            conversationRefreshTimer = window.setInterval(() => {
                if (currentUser && document.visibilityState === "visible") {
                    loadConversations().catch((error) => showError(error.message));
                }
            }, 20000);
        }
    } catch (error) {
        setStatus("disconnected");
        showError(error.message);
    }
}

async function openRequestedConversation() {
    const requestedId = Number(new URLSearchParams(window.location.search).get("conversationId"));
    if (!Number.isSafeInteger(requestedId) || requestedId <= 0) {
        return;
    }
    const requested = conversations.find((conversation) => conversation.id === requestedId);
    if (requested) {
        await openConversation(requested);
    }
    window.history.replaceState(null, "", window.location.pathname);
}

function decodeApplicationServerKey(value) {
    const padded = value + "=".repeat((4 - (value.length % 4)) % 4);
    const binary = window.atob(padded.replace(/-/g, "+").replace(/_/g, "/"));
    const result = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index++) {
        result[index] = binary.charCodeAt(index);
    }
    return result;
}

async function configureNotifications() {
    if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
        els.enableNotifications.hidden = true;
        return;
    }
    const config = await api("/api/push/config");
    if (!config.enabled) {
        els.enableNotifications.hidden = true;
        return;
    }
    pushRegistration = await navigator.serviceWorker.register("/service-worker.js");
    const subscription = await pushRegistration.pushManager.getSubscription();
    if (subscription && Notification.permission === "granted") {
        await savePushSubscription(subscription);
        els.enableNotifications.textContent = "Notifications on";
        els.enableNotifications.disabled = true;
    } else if (Notification.permission === "denied") {
        els.enableNotifications.textContent = "Notifications blocked";
        els.enableNotifications.disabled = true;
        els.enableNotifications.hidden = false;
    } else {
        els.enableNotifications.textContent = "Enable notifications";
        els.enableNotifications.disabled = false;
        els.enableNotifications.hidden = false;
    }
}

async function enableNotifications() {
    clearError();
    try {
        const permission = await Notification.requestPermission();
        if (permission !== "granted") {
            throw new Error("Allow notifications in your browser to receive chat alerts.");
        }
        if (!pushRegistration) {
            pushRegistration = await navigator.serviceWorker.register("/service-worker.js");
        }
        const config = await api("/api/push/config");
        if (!config.enabled) {
            throw new Error("Web Push is not configured on this server.");
        }
        const subscription = await pushRegistration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: decodeApplicationServerKey(config.applicationServerKey),
        });
        await savePushSubscription(subscription);
        els.enableNotifications.textContent = "Notifications on";
        els.enableNotifications.disabled = true;
        els.enableNotifications.hidden = false;
        showNotice("Browser notifications are enabled.");
    } catch (error) {
        showError(error.message);
    }
}

async function savePushSubscription(subscription) {
    await api("/api/push/subscriptions", {
        method: "POST",
        body: subscription.toJSON(),
    });
}

function connectSocket() {
    const scheme = window.location.protocol === "https:" ? "wss:" : "ws:";
    stompClient = new StompJs.Client({
        brokerURL: scheme + "//" + window.location.host + "/ws",
        reconnectDelay: 3000,
        onConnect: () => {
            setStatus("connected");
            stompClient.subscribe("/user/queue/errors", (frame) => {
                const message = JSON.parse(frame.body);
                showError(message.message);
            });
            if (activeConversation) {
                subscribeToConversation(activeConversation.id);
            }
        },
        onStompError: (frame) => showError(frame.headers.message || "The chat connection was rejected."),
        onWebSocketError: () => showError("Cannot reach the chat server. Check your connection and try again."),
        onWebSocketClose: () => setStatus("disconnected"),
    });
    stompClient.activate();
}

function setStatus(state) {
    const labels = { connecting: "Connecting", connected: "Connected", disconnected: "Offline" };
    els.status.textContent = labels[state];
    els.status.className = "status status--" + state;
    updateMessageControls();
}

function updateMessageControls() {
    const canSend = Boolean(stompClient && stompClient.connected && activeConversation);
    els.messageInput.disabled = !canSend;
    els.sendButton.disabled = !canSend;
}

async function logout() {
    clearError();
    if (stompClient) {
        await stompClient.deactivate();
        stompClient = null;
    }
    try {
        await api("/api/auth/logout", { method: "POST" });
        currentUser = null;
        if (conversationRefreshTimer !== null) {
            window.clearInterval(conversationRefreshTimer);
            conversationRefreshTimer = null;
        }
        conversations = [];
        activeConversation = null;
        els.authView.hidden = false;
        els.chatView.hidden = true;
        els.accountBar.hidden = true;
        els.authForm.reset();
        setAuthMode(false);
    } catch (error) {
        showError(error.message);
    }
}

async function loadConversations() {
    conversations = await api("/api/conversations");
    if (activeConversation) {
        const refreshed = conversations.find((conversation) => conversation.id === activeConversation.id);
        if (refreshed) {
            activeConversation = refreshed;
        }
    }
    renderConversations();
}

function renderConversations() {
    els.conversationList.replaceChildren();
    if (conversations.length === 0) {
        const hint = document.createElement("p");
        hint.className = "sidebar-hint";
        hint.textContent = "Your chats will show up here.";
        els.conversationList.append(hint);
        return;
    }
    conversations.forEach((conversation) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "conversation-item"
            + (activeConversation && activeConversation.id === conversation.id ? " is-active" : "");
        const heading = document.createElement("span");
        heading.className = "conversation-item-heading";
        const title = document.createElement("span");
        title.className = "conversation-item-title";
        title.textContent = conversation.type === "GROUP" ? "▧ " + conversation.title : conversation.title;
        heading.append(title);
        if (conversation.unreadCount > 0) {
            const badge = document.createElement("span");
            badge.className = "conversation-unread";
            badge.textContent = String(conversation.unreadCount);
            badge.setAttribute("aria-label", conversation.unreadCount + " unread messages");
            heading.append(badge);
        }
        const preview = document.createElement("span");
        preview.className = "conversation-preview";
        preview.textContent = conversation.lastMessage || conversation.members.join(", ");
        button.append(heading, preview);
        button.addEventListener("click", () => openConversation(conversation));
        els.conversationList.append(button);
    });
}

async function openConversation(conversation) {
    clearError();
    if (activeSubscription) {
        activeSubscription.unsubscribe();
        activeSubscription = null;
    }
    activeConversation = conversation;
    updateMessageControls();
    renderedMessageIds = new Set();
    hasOlderMessages = false;
    els.loadOlder.hidden = true;
    els.noConversation.hidden = true;
    els.panel.hidden = false;
    els.conversationTitle.textContent = conversation.title;
    els.conversationMembers.textContent = conversation.members.join(" · ");
    els.manageGroupButton.hidden = !(conversation.type === "GROUP" && conversation.owner);
    els.groupManager.hidden = true;
    els.messages.replaceChildren();
    renderConversations();
    if (stompClient && stompClient.connected) {
        subscribeToConversation(conversation.id);
    }
    try {
        const history = await api("/api/conversations/" + conversation.id + "/messages");
        hasOlderMessages = history.length === 50;
        els.loadOlder.hidden = !hasOlderMessages;
        history.forEach(renderMessage);
        updateEmptyHint();
        if (activeConversation && activeConversation.id === conversation.id
                && document.visibilityState === "visible") {
            await markConversationRead(conversation.id);
        }
    } catch (error) {
        showError(error.message);
    }
}

function subscribeToConversation(conversationId) {
    if (!stompClient || !stompClient.connected || !activeConversation
        || activeConversation.id !== conversationId) {
        return;
    }
    activeSubscription = stompClient.subscribe("/user/queue/conversations/" + conversationId, (frame) => {
        renderMessage(JSON.parse(frame.body));
        updateEmptyHint();
        if (document.visibilityState === "visible") {
            markConversationRead(conversationId).catch((error) => showError(error.message));
        }
        loadConversations().catch((error) => showError(error.message));
    });
}

async function markConversationRead(conversationId) {
    const updated = await api("/api/conversations/" + conversationId + "/read", { method: "POST" });
    conversations = conversations.map((conversation) => conversation.id === conversationId ? updated : conversation);
    if (activeConversation && activeConversation.id === conversationId) {
        activeConversation = updated;
    }
    renderConversations();
}

function renderMessage(message, preserveScroll) {
    if (renderedMessageIds.has(message.id)) {
        return;
    }
    renderedMessageIds.add(message.id);
    const previousHeight = els.messages.scrollHeight;
    const previousTop = els.messages.scrollTop;
    const box = document.createElement("article");
    box.className = "message" + (message.sender === "@" + currentUser.handle ? " message--own" : "");
    box.dataset.messageId = String(message.id);
    const meta = document.createElement("div");
    meta.className = "message-meta";
    const sender = document.createElement("strong");
    sender.textContent = message.sender;
    const time = document.createElement("time");
    const date = new Date(message.timestamp);
    time.dateTime = message.timestamp;
    time.textContent = Number.isNaN(date.getTime()) ? "" : date.toLocaleTimeString([], {
        hour: "numeric",
        minute: "2-digit",
    });
    meta.append(sender, time);
    const text = document.createElement("p");
    text.className = "message-text";
    text.textContent = message.content;
    box.append(meta, text);
    const nextMessage = Array.from(els.messages.querySelectorAll(".message"))
        .find((element) => Number(element.dataset.messageId) > message.id);
    if (nextMessage) {
        els.messages.insertBefore(box, nextMessage);
    } else {
        els.messages.append(box);
    }
    if (preserveScroll) {
        els.messages.scrollTop = previousTop + els.messages.scrollHeight - previousHeight;
    } else {
        els.messages.scrollTop = els.messages.scrollHeight;
    }
}

function updateEmptyHint() {
    const hint = els.messages.querySelector(".empty-hint");
    if (els.messages.querySelector(".message")) {
        if (hint) {
            hint.remove();
        }
    } else if (!hint) {
        const emptyHint = document.createElement("p");
        emptyHint.className = "empty-hint";
        emptyHint.textContent = "No messages yet. Say hello!";
        els.messages.append(emptyHint);
    }
}

async function loadOlderMessages() {
    const firstMessage = els.messages.querySelector(".message");
    if (!activeConversation || !firstMessage || !hasOlderMessages) {
        return;
    }
    const beforeId = firstMessage.dataset.messageId;
    try {
        const older = await api("/api/conversations/" + activeConversation.id
            + "/messages?beforeId=" + encodeURIComponent(beforeId));
        older.forEach((message) => renderMessage(message, true));
        hasOlderMessages = older.length === 50;
        els.loadOlder.hidden = !hasOlderMessages;
    } catch (error) {
        showError(error.message);
    }
}

async function sendMessage(event) {
    event.preventDefault();
    if (!stompClient || !stompClient.connected || !activeConversation) {
        showError("Connect to chat before sending a message.");
        return;
    }
    const content = els.messageInput.value;
    if (!content.trim()) {
        showError("Message must not be blank.");
        return;
    }
    clearError();
    stompClient.publish({
        destination: "/app/conversations/" + activeConversation.id + "/send",
        body: JSON.stringify({ content }),
    });
    els.messageInput.value = "";
    updateCounter();
    els.messageInput.focus();
}

function updateCounter() {
    const length = els.messageInput.value.length;
    els.counter.textContent = length + " / " + MAX_MESSAGE_LENGTH;
    els.counter.classList.toggle("counter--over", length > MAX_MESSAGE_LENGTH);
}

async function searchUsers() {
    const query = els.userSearch.value.trim();
    els.searchResults.replaceChildren();
    if (query.replace(/^@/, "").length < 2) {
        return;
    }
    try {
        const users = await api("/api/users?handle=" + encodeURIComponent(query));
        users.forEach((user) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "search-result";
            button.textContent = "@" + user.handle;
            button.addEventListener("click", async () => {
                try {
                    const conversation = await api("/api/conversations/direct", {
                        method: "POST",
                        body: { handle: user.handle },
                    });
                    els.userSearch.value = "";
                    els.searchResults.replaceChildren();
                    await loadConversations();
                    const current = conversations.find((item) => item.id === conversation.id) || conversation;
                    await openConversation(current);
                } catch (error) {
                    showError(error.message);
                }
            });
            els.searchResults.append(button);
        });
        if (users.length === 0) {
            const hint = document.createElement("p");
            hint.className = "sidebar-hint";
            hint.textContent = "No users found.";
            els.searchResults.append(hint);
        }
    } catch (error) {
        showError(error.message);
    }
}

async function createGroup(event) {
    event.preventDefault();
    clearError();
    try {
        const handles = els.groupMembers.value.split(",").map((handle) => handle.trim()).filter(Boolean);
        const conversation = await api("/api/conversations/groups", {
            method: "POST",
            body: { title: els.groupTitle.value, handles },
        });
        els.groupForm.reset();
        els.groupForm.closest("details").open = false;
        await loadConversations();
        const current = conversations.find((item) => item.id === conversation.id) || conversation;
        await openConversation(current);
    } catch (error) {
        showError(error.message);
    }
}

function renderMembers() {
    if (!activeConversation) {
        return;
    }
    els.conversationMembers.textContent = activeConversation.members.join(" · ");
    els.memberList.replaceChildren();
    activeConversation.members.forEach((handle) => {
        const item = document.createElement("li");
        const label = document.createElement("span");
        label.textContent = handle + (handle === "@" + currentUser.handle && activeConversation.owner ? " · creator" : "");
        item.append(label);
        if (activeConversation.owner && handle !== "@" + currentUser.handle) {
            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "text-button";
            remove.textContent = "Remove";
            remove.addEventListener("click", () => removeMember(handle));
            item.append(remove);
        }
        els.memberList.append(item);
    });
}

async function addMember(event) {
    event.preventDefault();
    clearError();
    if (!activeConversation) {
        return;
    }
    try {
        activeConversation = await api("/api/conversations/" + activeConversation.id + "/members", {
            method: "POST",
            body: { handle: els.newMember.value },
        });
        els.newMember.value = "";
        renderMembers();
        await loadConversations();
    } catch (error) {
        showError(error.message);
    }
}

async function removeMember(handle) {
    clearError();
    try {
        activeConversation = await api("/api/conversations/" + activeConversation.id + "/members/"
            + encodeURIComponent(handle), { method: "DELETE" });
        renderMembers();
        await loadConversations();
    } catch (error) {
        showError(error.message);
    }
}

async function bootstrap() {
    setAuthMode(false);
    updateCounter();
    try {
        await refreshCsrf();
        currentUser = await api("/api/auth/me");
        await enterChat();
    } catch (error) {
        currentUser = null;
        els.authView.hidden = false;
        els.chatView.hidden = true;
        els.accountBar.hidden = true;
        if (error.status !== 401 && error.status !== 403) {
            showError("Could not reach ClassChat. Check that the server is running and try again.");
        }
    }
}

document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
        loadConversations().catch((error) => showError(error.message));
        if (activeConversation) {
            markConversationRead(activeConversation.id).catch((error) => showError(error.message));
        }
    }
});

if ("serviceWorker" in navigator) {
    navigator.serviceWorker.addEventListener("message", (event) => {
        if (!event.data || event.data.type !== "open-conversation") {
            return;
        }
        const conversationId = Number(event.data.conversationId);
        if (!Number.isSafeInteger(conversationId) || conversationId <= 0) {
            return;
        }
        const targetUrl = "/?conversationId=" + conversationId;
        window.history.replaceState(null, "", targetUrl);
        if (currentUser) {
            const conversation = conversations.find((item) => item.id === conversationId);
            if (conversation) {
                openConversation(conversation).catch((error) => showError(error.message));
            }
        }
    });
}

els.authForm.addEventListener("submit", submitAuth);
els.authSwitch.addEventListener("click", () => setAuthMode(!signingUp));
els.resendCode.addEventListener("click", resendVerificationCode);
els.legacyVerificationLink.addEventListener("click", showLegacyVerification);
els.legacySendCode.addEventListener("click", requestLegacyVerificationCode);
els.legacyResendCode.addEventListener("click", requestLegacyVerificationCode);
els.legacyChangeEmail.addEventListener("click", () => {
    els.legacyEmail.disabled = false;
    els.legacyCodeStage.hidden = true;
    els.legacyCode.required = false;
    els.legacyCode.value = "";
    els.legacySendCode.hidden = false;
    els.legacyChangeEmail.hidden = true;
});
els.legacyBack.addEventListener("click", () => returnToSignIn(null));
els.legacyVerificationForm.addEventListener("submit", verifyLegacyEmail);
els.forgotPasswordLink.addEventListener("click", showPasswordReset);
els.resetSendCode.addEventListener("click", requestPasswordResetCode);
els.resetResendCode.addEventListener("click", requestPasswordResetCode);
els.resetChangeEmail.addEventListener("click", () => {
    els.resetEmail.disabled = false;
    els.resetCodeStage.hidden = true;
    els.resetCode.required = false;
    els.resetNewPassword.required = false;
    els.resetConfirmPassword.required = false;
    els.resetCode.value = "";
    els.resetNewPassword.value = "";
    els.resetConfirmPassword.value = "";
    els.resetSendCode.hidden = false;
    els.resetChangeEmail.hidden = true;
});
els.resetBack.addEventListener("click", () => returnToSignIn(null));
els.passwordResetForm.addEventListener("submit", submitPasswordReset);
els.enableNotifications.addEventListener("click", enableNotifications);
els.logout.addEventListener("click", logout);
els.userSearch.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(searchUsers, 250);
});
els.searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    searchUsers();
});
els.groupForm.addEventListener("submit", createGroup);
els.refresh.addEventListener("click", () => loadConversations().catch((error) => showError(error.message)));
els.messageForm.addEventListener("submit", sendMessage);
els.messageInput.addEventListener("input", updateCounter);
els.loadOlder.addEventListener("click", loadOlderMessages);
els.manageGroupButton.addEventListener("click", () => {
    els.groupManager.hidden = !els.groupManager.hidden;
    renderMembers();
});
els.addMemberForm.addEventListener("submit", addMember);
bootstrap();
