(function () {
    "use strict";

    function createWidget(options) {
        const config = Object.assign({
            title: "AI 农业助手",
            placeholder: "输入问题，如：统计一下各地区地块面积",
            sessionId: "session_" + Date.now()
        }, options || {});

        if (document.getElementById("aiChatPanel")) {
            return;
        }

        const toggle = document.createElement("button");
        toggle.className = "ai-chat-toggle";
        toggle.id = "aiChatToggle";
        toggle.type = "button";
        toggle.innerHTML = '<i class="fa fa-robot"></i>';

        const panel = document.createElement("div");
        panel.id = "aiChatPanel";
        panel.className = "ai-chat-panel hidden";
        panel.innerHTML =
            '<div class="ai-chat-header">' +
            '  <span><i class="fa fa-robot"></i> ' + config.title + '</span>' +
            '  <div>' +
            '    <button id="aiChatFullscreen" title="全屏"><i class="fa fa-expand"></i></button>' +
            '    <button id="aiChatClose">&times;</button>' +
            '  </div>' +
            '</div>' +
            '<div id="aiChatMessages" class="ai-chat-messages"></div>' +
            '<div class="ai-chat-input-area">' +
            '  <textarea id="aiChatInput" rows="1"></textarea>' +
            '  <button id="aiChatSend">发送</button>' +
            '</div>';

        document.body.appendChild(toggle);
        document.body.appendChild(panel);
        initAiChat(config);
    }

    function initAiChat(config) {
        const toggle = document.getElementById("aiChatToggle");
        const panel = document.getElementById("aiChatPanel");
        const closeBtn = document.getElementById("aiChatClose");
        const sendBtn = document.getElementById("aiChatSend");
        const input = document.getElementById("aiChatInput");
        const messages = document.getElementById("aiChatMessages");
        const fsBtn = document.getElementById("aiChatFullscreen");
        const token = localStorage.getItem("token_admin") || "";
        let chatSessionId = config.sessionId || ("session_" + Date.now());

        input.placeholder = config.placeholder || "输入问题";

        if (typeof marked !== "undefined") {
            marked.setOptions({breaks: true, gfm: true});
        }

        function renderMarkdown(text) {
            if (typeof marked === "undefined") {
                return text;
            }
            const clean = String(text || "").replace(/\n{2,}/g, "\n\n");
            return marked.parse(clean);
        }

        function fixLinks(container) {
            container.querySelectorAll("a").forEach(function (a) {
                a.setAttribute("target", "_blank");
                a.setAttribute("rel", "noopener noreferrer");
            });
        }

        toggle.onclick = function () {
            panel.classList.toggle("hidden");
            toggle.style.animation = panel.classList.contains("hidden") ? "" : "none";
            if (!panel.classList.contains("hidden")) {
                input.focus();
            }
        };

        closeBtn.onclick = function () {
            panel.classList.add("hidden");
            toggle.style.animation = "";
        };

        fsBtn.onclick = function () {
            panel.classList.toggle("fullscreen");
            fsBtn.querySelector("i").className = panel.classList.contains("fullscreen") ? "fa fa-compress" : "fa fa-expand";
        };

        sendBtn.onclick = function () {
            sendChatMessage();
        };

        input.addEventListener("keydown", function (e) {
            if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                sendChatMessage();
            }
        });

        async function sendChatMessage() {
            const msg = input.value.trim();
            if (!msg || sendBtn.disabled) {
                return;
            }
            input.value = "";

            appendMessage("user", msg);
            const responseDiv = appendMessage("assistant", "", true);
            sendBtn.disabled = true;

            try {
                const res = await fetch("/admin/ai/chat/stream", {
                    method: "POST",
                    headers: {"Content-Type": "application/json", "token": token},
                    body: JSON.stringify({sessionId: chatSessionId, message: msg})
                });

                if (!res.ok) {
                    const errText = await res.text();
                    responseDiv.textContent = "请求失败 (" + res.status + "): " + errText;
                    responseDiv.classList.remove("streaming-cursor");
                    return;
                }

                const reader = res.body.getReader();
                const decoder = new TextDecoder();
                let buffer = "";
                let accumulatedMarkdown = "";
                let chainPanel = null;
                let chainBody = null;
                let currentStepDiv = null;
                let toolCallCounter = 0;

                while (true) {
                    const result = await reader.read();
                    if (result.done) {
                        break;
                    }

                    buffer += decoder.decode(result.value, {stream: true});
                    const lines = buffer.split("\n");
                    buffer = lines.pop();

                    for (const line of lines) {
                        const trimmed = line.trim();
                        if (!trimmed.startsWith("data:")) {
                            continue;
                        }

                        try {
                            const data = JSON.parse(trimmed.substring(trimmed.indexOf(":") + 1));
                            if (data.type === "step") {
                                if (!chainPanel) {
                                    chainPanel = document.createElement("div");
                                    chainPanel.className = "chain-panel";
                                    const header = document.createElement("div");
                                    header.className = "chain-header";
                                    header.innerHTML = '<span class="arrow">&#9660;</span> Agent 过程';
                                    header.onclick = function () {
                                        header.classList.toggle("collapsed");
                                        chainBody.classList.toggle("hidden");
                                    };
                                    chainBody = document.createElement("div");
                                    chainBody.className = "chain-body";
                                    chainPanel.appendChild(header);
                                    chainPanel.appendChild(chainBody);
                                    messages.insertBefore(chainPanel, responseDiv.parentElement);
                                }

                                currentStepDiv = document.createElement("div");
                                currentStepDiv.className = "chain-step";
                                currentStepDiv.innerHTML = '<div class="chain-step-label">步骤 ' + data.step + "</div>";
                                chainBody.appendChild(currentStepDiv);
                                toolCallCounter = 0;
                                messages.scrollTop = messages.scrollHeight;
                            } else if (data.type === "thought") {
                                if (currentStepDiv) {
                                    const thoughtDiv = document.createElement("div");
                                    thoughtDiv.className = "chain-thought";
                                    thoughtDiv.textContent = data.content || "";
                                    currentStepDiv.appendChild(thoughtDiv);
                                    accumulatedMarkdown = "";
                                    responseDiv.innerHTML = "";
                                    messages.scrollTop = messages.scrollHeight;
                                }
                            } else if (data.type === "token") {
                                accumulatedMarkdown += data.content || "";
                                if (typeof marked !== "undefined") {
                                    responseDiv.innerHTML = renderMarkdown(accumulatedMarkdown);
                                    fixLinks(responseDiv);
                                } else {
                                    responseDiv.textContent = accumulatedMarkdown;
                                }
                                messages.scrollTop = messages.scrollHeight;
                            } else if (data.type === "tool_start") {
                                if (currentStepDiv) {
                                    toolCallCounter++;
                                    const actionDiv = document.createElement("div");
                                    actionDiv.className = "chain-action";
                                    actionDiv.innerHTML = '<span class="spinner">&#8635;</span> ' + (data.tool_name || "");
                                    actionDiv.id = "action-" + toolCallCounter;
                                    actionDiv.dataset.toolName = data.tool_name || "";
                                    currentStepDiv.appendChild(actionDiv);
                                }
                                messages.scrollTop = messages.scrollHeight;
                            } else if (data.type === "tool_result") {
                                if (currentStepDiv) {
                                    const actionEls = currentStepDiv.querySelectorAll(".chain-action");
                                    let actionEl = null;
                                    for (let i = actionEls.length - 1; i >= 0; i--) {
                                        if (actionEls[i].dataset.toolName === data.tool_name && actionEls[i].querySelector(".spinner")) {
                                            actionEl = actionEls[i];
                                            break;
                                        }
                                    }
                                    if (actionEl) {
                                        const spinner = actionEl.querySelector(".spinner");
                                        if (spinner) {
                                            spinner.remove();
                                        }
                                    }
                                    if (data.result_preview) {
                                        const obsDiv = document.createElement("div");
                                        obsDiv.className = "chain-observation";
                                        obsDiv.textContent = data.result_preview;
                                        currentStepDiv.appendChild(obsDiv);
                                    }
                                }
                                messages.scrollTop = messages.scrollHeight;
                            } else if (data.type === "done") {
                                responseDiv.classList.remove("streaming-cursor");
                                if (chainPanel && chainBody) {
                                    setTimeout(function () {
                                        const header = chainPanel.querySelector(".chain-header");
                                        if (header) {
                                            header.classList.add("collapsed");
                                        }
                                        chainBody.classList.add("hidden");
                                    }, 3000);
                                }
                            }
                        } catch (e) {
                            console.warn("SSE parse error:", e, trimmed);
                        }
                    }
                }

                responseDiv.classList.remove("streaming-cursor");
            } catch (err) {
                responseDiv.textContent = "错误: " + err.message;
                responseDiv.classList.remove("streaming-cursor");
            } finally {
                sendBtn.disabled = false;
            }
            messages.scrollTop = messages.scrollHeight;
        }

        function appendMessage(role, content, isStreaming) {
            const div = document.createElement("div");
            div.className = "chat-msg " + role;
            const bubble = document.createElement("div");
            bubble.className = "chat-bubble";
            if (isStreaming) {
                bubble.classList.add("streaming-cursor");
            } else if (typeof marked !== "undefined" && role === "assistant") {
                bubble.innerHTML = renderMarkdown(content);
                fixLinks(bubble);
            } else {
                bubble.textContent = content;
            }
            div.appendChild(bubble);
            messages.appendChild(div);
            messages.scrollTop = messages.scrollHeight;
            return bubble;
        }
    }

    window.AdminAiChat = {init: createWidget};
})();
