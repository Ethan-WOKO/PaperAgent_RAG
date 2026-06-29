<template>
  <AppLayout>
    <div class="chat-page research-chat-page">
      <aside class="chat-sidebar">
        <NCard size="small" class="chat-panel chat-session-panel">
          <template #header>
            <div class="panel-heading">
              <span>Recent Conversations</span>
              <small>{{ sessions.length }} sessions</small>
            </div>
          </template>
          <template #header-extra>
            <NButton type="primary" size="small" circle @click="handleCreateSession">+</NButton>
          </template>

          <div class="chat-sidebar__hint">会话保留模型、RAG 和工具设置。建议按任务主题拆分对话。</div>

          <NSpace vertical :size="8">
            <NEmpty v-if="sessions.length === 0" description="还没有会话" size="small" />
            <NButton
              v-for="session in sessions"
              :key="session.id"
              block
              text
              class="session-item"
              :class="{ 'session-item--active': selectedSessionId === session.id }"
              @click="selectSession(session.id)"
            >
              <div class="session-item__content">
                <div class="session-item__title">{{ session.title || `会话 #${session.id}` }}</div>
                <div class="session-item__meta">{{ session.modelProvider }} · {{ session.model }}</div>
                <div class="session-item__meta">max_steps {{ session.maxSteps }}</div>
              </div>
            </NButton>
          </NSpace>
        </NCard>
      </aside>

      <section class="chat-main">
        <NCard
          class="chat-panel chat-workspace-panel"
          :content-style="{ height: 'calc(100% - 76px)', display: 'flex', flexDirection: 'column', gap: '14px' }"
        >
          <template #header>
            <div class="chat-room-title">
              <div>
                <h2>{{ activeSessionTitle }}</h2>
                <p>Ask a research question, search literature, or describe a task.</p>
              </div>
              <div class="chat-room-tags">
                <span>Literature Review</span>
                <span>Agent Workflow</span>
                <span>Knowledge RAG</span>
              </div>
            </div>
          </template>
          <template #header-extra>
            <div class="chat-toolbar">
              <NCheckbox v-model:checked="ragDisabled">禁用知识库</NCheckbox>
              <NSelect
                v-model:value="selectedSkillId"
                style="width: 220px"
                clearable
                :options="skillOptions"
                placeholder="Skill（可选）"
              />
              <NCheckbox v-model:checked="showProcessMessages">过程</NCheckbox>
              <NButton secondary round @click="reloadCurrentMessages" :disabled="!selectedSessionId">刷新</NButton>
            </div>
          </template>

          <div class="chat-intro-bar">
            <div>
              <div class="chat-intro-bar__title">Research Copilot Workspace</div>
              <div class="chat-intro-bar__desc">支持 `/literature topic 5篇 bibtex` 检索文献；后续 Agent 将在右侧展示任务拆解、工具调用和执行轨迹。</div>
            </div>
            <div class="chat-intro-bar__status"><span /> Live</div>
          </div>

          <div ref="messagesContainerRef" class="chat-messages">
            <NEmpty v-if="filteredMessages.length === 0" description="发一条消息开始对话" class="chat-empty" />
            <div v-for="message in filteredMessages" :key="message.localId" class="message-row" :class="`message-row--${message.role}`">
              <template v-if="message.role === 'system' || message.role === 'tool'">
                <details class="process-message-card">
                  <summary class="process-message-card__summary">
                    <span>{{ message.role === 'system' ? 'System process' : 'Tool output' }}</span>
                    <span class="process-message-card__meta">展开</span>
                  </summary>
                  <div class="process-message-card__content">
                    <template v-for="(segment, index) in getMessageSegments(message.content)" :key="`${message.localId}-${index}`">
                      <pre v-if="segment.type === 'code'" class="message-code-block"><code>{{ segment.content }}</code></pre>
                      <p v-else class="message-text-block">{{ segment.content }}</p>
                    </template>
                  </div>
                </details>
              </template>

              <template v-else>
                <div class="message-avatar" :class="`message-avatar--${message.role}`">{{ message.role === 'user' ? '你' : '✦' }}</div>
                <div class="message-bubble">
                  <div class="message-role">{{ message.role === 'user' ? 'You' : 'ScholarAI' }}</div>
                  <div class="message-content">
                    <template v-for="(segment, index) in getMessageSegments(message.content || (message.role === 'assistant' ? '正在思考...' : '...'))" :key="`${message.localId}-${index}`">
                      <pre v-if="segment.type === 'code'" class="message-code-block"><code>{{ segment.content }}</code></pre>
                      <p v-else class="message-text-block">{{ segment.content }}</p>
                    </template>
                  </div>
                  <NButton
                    v-if="message.navigationUrl"
                    text
                    type="primary"
                    class="message-link-button"
                    @click="goToNavigation(message.navigationUrl)"
                  >
                    打开论文修改页
                  </NButton>
                </div>
              </template>
            </div>
          </div>

          <div class="chat-composer">
            <div class="chat-composer__quick-actions">
              <button type="button" @click="draft = '/literature polarimetric FDA-MIMO self-protection jamming 5篇 bibtex'">Search Papers</button>
              <button type="button" @click="draft = '帮我润色论文'">Polish Paper</button>
              <button type="button" @click="showProcessMessages = !showProcessMessages">Tool Trace</button>
            </div>
            <NInput
              v-model:value="draft"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 7 }"
              placeholder="Ask a research question or describe a task..."
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="chat-composer__footer">
              <span class="chat-hint">Enter 发送 · Shift+Enter 换行</span>
              <NButton type="primary" round :loading="sending" @click="handleSend">发送 →</NButton>
            </div>
          </div>
        </NCard>
      </section>

      <aside class="agent-sidebar">
        <section class="agent-card agent-card--plan">
          <div class="agent-card__head">
            <div>
              <strong>Research Agent</strong>
              <span>Agent Plan</span>
            </div>
            <em>Live</em>
          </div>
          <div class="agent-progress"><span :style="{ width: sending ? '62%' : '36%' }" /></div>
          <div class="agent-plan-list">
            <div class="agent-plan-step agent-plan-step--done"><i>1</i><span>Understand request</span><small>Done</small></div>
            <div class="agent-plan-step" :class="{ 'agent-plan-step--active': sending }"><i>2</i><span>Search literature</span><small>{{ sending ? 'Running' : 'Ready' }}</small></div>
            <div class="agent-plan-step"><i>3</i><span>Analyze papers</span><small>Pending</small></div>
            <div class="agent-plan-step"><i>4</i><span>Draft response</span><small>Pending</small></div>
            <div class="agent-plan-step"><i>5</i><span>Verify citations</span><small>Pending</small></div>
          </div>
        </section>

        <section class="agent-card">
          <div class="agent-card__head"><strong>Tools & Execution</strong><a>View all</a></div>
          <div class="tool-call-row"><span>⌕</span><div><strong>search_literature</strong><small>OpenAlex / arXiv / local cards</small></div><em>Ready</em></div>
          <div class="tool-call-row"><span>▣</span><div><strong>search_knowledge</strong><small>Private RAG retrieval</small></div><em>{{ ragDisabled ? 'Off' : 'Ready' }}</em></div>
          <div class="tool-call-row"><span>✎</span><div><strong>paper polish</strong><small>Critic / repair workflow</small></div><em>Ready</em></div>
          <div class="tool-call-row"><span>⚙</span><div><strong>skill mode</strong><small>{{ selectedSkillId || 'No skill selected' }}</small></div><em>{{ selectedSkillId ? 'On' : 'Idle' }}</em></div>
        </section>

        <section class="agent-card">
          <div class="agent-card__head"><strong>Execution Trace</strong><span>{{ messages.length }} msgs</span></div>
          <div class="execution-log">
            <div><time>Now</time><span>Workspace ready</span><em>Info</em></div>
            <div v-if="sending"><time>Now</time><span>Agent response streaming</span><em>Running</em></div>
            <div><time>RAG</time><span>{{ ragDisabled ? 'Knowledge disabled' : 'Knowledge enabled' }}</span><em>{{ ragDisabled ? 'Off' : 'Ready' }}</em></div>
          </div>
        </section>
      </aside>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { NButton, NCard, NCheckbox, NEmpty, NInput, NSelect, NSpace } from 'naive-ui';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/components/AppLayout.vue';
import { createSession, listMessages, listSessions, type AgentMessageResponse, type AgentSessionResponse } from '@/api/agent';
import { listSkills, type SkillListItemResponse } from '@/api/skills';
import { useAuthStore } from '@/stores/auth';
import { ui } from '@/ui';

type MessageRole = 'user' | 'assistant' | 'system' | 'tool';

interface ChatMessageView {
  localId: string;
  role: MessageRole;
  content: string;
  toolCallsJson?: string | null;
  navigationUrl?: string | null;
}

interface WsChatEvent {
  type: 'chunk' | 'done' | 'error';
  content: string | null;
  sessionId: number | null;
  error: string | null;
  finishReason: string | null;
  navigationUrl: string | null;
}

interface MessageSegment {
  type: 'text' | 'code';
  content: string;
}

const router = useRouter();
const authStore = useAuthStore();
const sessions = ref<AgentSessionResponse[]>([]);
const selectedSessionId = ref<number | null>(null);
const messages = ref<ChatMessageView[]>([]);
const draft = ref('');
const sending = ref(false);
const selectedSkillId = ref<string | null>(null);
const availableSkills = ref<SkillListItemResponse[]>([]);
const ragDisabled = ref(false);
const showProcessMessages = ref(false);
const currentSocket = ref<WebSocket | null>(null);
const currentAssistantMessageId = ref<string | null>(null);
const messagesContainerRef = ref<HTMLElement | null>(null);

const skillOptions = computed(() => availableSkills.value
  .filter((skill) => skill.enabled)
  .map((skill) => ({ label: `${skill.name}${skill.source === 'builtin' ? '（内置）' : ''}`, value: skill.id })));

const activeSessionTitle = computed(() => {
  const active = sessions.value.find((item) => item.id === selectedSessionId.value);
  return active?.title || '研伴对话';
});

const filteredMessages = computed(() => {
  const visibleMessages = messages.value.filter((message) => !isEmptyAssistantToolCallMessage(message));
  if (showProcessMessages.value) {
    return visibleMessages;
  }
  return visibleMessages.filter((message) => message.role === 'user' || message.role === 'assistant');
});

onMounted(async () => {
  await Promise.all([loadSessions(), loadSkills()]);
});

onBeforeUnmount(() => {
  currentSocket.value?.close();
});

async function loadSkills() {
  try {
    const { data } = await listSkills();
    availableSkills.value = data;
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '加载 Skills 失败');
  }
}

async function loadSessions(selectLatest = true) {
  const { data } = await listSessions();
  sessions.value = data;
  if (selectLatest && data.length > 0) {
    await selectSession(data[0].id);
  }
}

async function selectSession(sessionId: number) {
  selectedSessionId.value = sessionId;
  const session = sessions.value.find((item) => item.id === sessionId);
  ragDisabled.value = Boolean(session?.ragDisabled);
  await reloadCurrentMessages();
}

async function reloadCurrentMessages() {
  if (!selectedSessionId.value) {
    messages.value = [];
    return;
  }
  const { data } = await listMessages(selectedSessionId.value);
  messages.value = data.map(toViewMessage);
  await scrollMessagesToBottom();
}

async function handleCreateSession() {
  try {
    const { data } = await createSession({ title: '新会话', ragDisabled: false });
    sessions.value = [data, ...sessions.value];
    await selectSession(data.id);
    ui.message.success('已创建新会话');
  } catch (error: any) {
    ui.message.error(error.response?.data?.message || '创建会话失败');
  }
}

async function handleSend() {
  if (!draft.value.trim()) {
    ui.message.warning('请输入消息内容');
    return;
  }
  if (sending.value) {
    return;
  }

  try {
    sending.value = true;
    let sessionId = selectedSessionId.value;
    if (!sessionId) {
      const title = draft.value.trim().slice(0, 20) || '新会话';
      const { data } = await createSession({ title, ragDisabled: ragDisabled.value });
      sessions.value = [data, ...sessions.value];
      selectedSessionId.value = data.id;
      sessionId = data.id;
    }

    const content = draft.value.trim();
    draft.value = '';
    messages.value.push({
      localId: `user-${Date.now()}`,
      role: 'user',
      content,
    });

    const assistantId = `assistant-${Date.now()}`;
    currentAssistantMessageId.value = assistantId;
    messages.value.push({
      localId: assistantId,
      role: 'assistant',
      content: '',
      navigationUrl: null,
    });
    await scrollMessagesToBottom();

    await sendWsMessage(sessionId, content, ragDisabled.value, selectedSkillId.value);
  } catch (error: any) {
    removePendingAssistant();
    ui.message.error(error.response?.data?.message || error.message || '发送失败');
    sending.value = false;
  }
}

async function sendWsMessage(sessionId: number, content: string, disableRag: boolean, skillId: string | null) {
  currentSocket.value?.close();

  await new Promise<void>((resolve, reject) => {
    let settled = false;
    const token = authStore.token;
    if (!token) {
      reject(new Error('未登录'));
      return;
    }

    const finishResolve = () => {
      if (settled) {
        return;
      }
      settled = true;
      resolve();
    };

    const finishReject = (error: Error) => {
      if (settled) {
        return;
      }
      settled = true;
      reject(error);
    };

    const backendHttpBase = import.meta.env.DEV
      ? 'http://localhost:8080'
      : window.location.origin;
    const wsBase = backendHttpBase.replace(/^http/, 'ws');
    const ws = new WebSocket(`${wsBase}/api/v1/ws/chat?token=${encodeURIComponent(token)}`);
    currentSocket.value = ws;

    ws.onopen = () => {
      ws.send(JSON.stringify({
        sessionId,
        content,
        ragDisabled: disableRag,
        skillId,
      }));
    };

    ws.onmessage = async (event) => {
      const payload = JSON.parse(event.data) as WsChatEvent;
      if (payload.type === 'chunk' && payload.content) {
        appendAssistantChunk(payload.content);
        return;
      }
      if (payload.type === 'error') {
        finishReject(new Error(payload.error || 'WebSocket 对话失败'));
        ws.close();
        return;
      }
      if (payload.type === 'done') {
        if (payload.navigationUrl) {
          attachAssistantNavigation(payload.navigationUrl);
        }
        await afterSendFinished(sessionId);
        ws.close();
        finishResolve();
      }
    };

    ws.onerror = () => finishReject(new Error('WebSocket 连接失败'));
    ws.onclose = () => {
      currentSocket.value = null;
      if (!settled && sending.value) {
        finishReject(new Error('WebSocket 连接已关闭'));
      }
    };
  });
}

async function appendAssistantChunk(chunk: string) {
  const target = messages.value.find((item) => item.localId === currentAssistantMessageId.value);
  if (target) {
    target.content += chunk;
    await scrollMessagesToBottom();
  }
}

async function scrollMessagesToBottom() {
  await nextTick();
  const container = messagesContainerRef.value;
  if (!container) {
    return;
  }
  container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' });
}

function attachAssistantNavigation(navigationUrl: string) {
  const target = messages.value.find((item) => item.localId === currentAssistantMessageId.value);
  if (target) {
    target.navigationUrl = navigationUrl;
  }
}

function removePendingAssistant() {
  if (!currentAssistantMessageId.value) {
    return;
  }
  messages.value = messages.value.filter((item) => item.localId !== currentAssistantMessageId.value);
  currentAssistantMessageId.value = null;
}

async function afterSendFinished(sessionId: number) {
  sending.value = false;
  currentAssistantMessageId.value = null;
  await reloadCurrentMessages();
  const { data } = await listSessions();
  sessions.value = data;
  selectedSessionId.value = sessionId;
}

function toViewMessage(message: AgentMessageResponse): ChatMessageView {
  return {
    localId: `server-${message.id}`,
    role: normalizeRole(message.role),
    content: message.content,
    toolCallsJson: message.toolCallsJson,
    navigationUrl: extractNavigationUrl(message.content),
  };
}

function isEmptyAssistantToolCallMessage(message: ChatMessageView) {
  return message.role === 'assistant'
    && Boolean(message.toolCallsJson)
    && !message.content?.trim();
}

function normalizeRole(role: string): MessageRole {
  switch ((role || '').toLowerCase()) {
    case 'assistant':
      return 'assistant';
    case 'system':
      return 'system';
    case 'tool':
      return 'tool';
    default:
      return 'user';
  }
}

function getMessageSegments(content: string): MessageSegment[] {
  const source = content || '';
  const regex = /```([\w-]+)?\n([\s\S]*?)```/g;
  const segments: MessageSegment[] = [];
  let lastIndex = 0;
  let matched: RegExpExecArray | null;

  while ((matched = regex.exec(source)) !== null) {
    const [fullMatch, , codeContent] = matched;
    const start = matched.index;
    if (start > lastIndex) {
      const text = source.slice(lastIndex, start).trim();
      if (text) {
        segments.push({ type: 'text', content: text });
      }
    }
    segments.push({ type: 'code', content: codeContent.trimEnd() });
    lastIndex = start + fullMatch.length;
  }

  if (lastIndex < source.length) {
    const text = source.slice(lastIndex).trim();
    if (text) {
      segments.push({ type: 'text', content: text });
    }
  }

  return segments.length > 0 ? segments : [{ type: 'text', content: source }];
}

function extractNavigationUrl(content: string) {
  const matched = content.match(/\/paper(?:\?[^\s]+)?/);
  return matched?.[0] || null;
}

function goToNavigation(navigationUrl: string) {
  void router.push(navigationUrl);
}
</script>
