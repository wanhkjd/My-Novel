<template>
  <div class="ai-companion-wrapper">
    <!-- 悬浮挂件按钮 -->
    <div
      class="ai-floating-btn"
      :class="{ 'has-unread': hasUnread, 'is-open': visible }"
      @click="toggleChat"
      title="点击打开 AI 智能书童"
    >
      <div class="ai-btn-inner">
        <span class="ai-icon">🤖</span>
        <span class="ai-btn-text">AI 书童</span>
      </div>
      <span class="pulse-ring"></span>
    </div>

    <!-- 智能书童聊天抽屉/弹窗 -->
    <el-drawer
      v-model="visible"
      :title="null"
      :with-header="false"
      size="440px"
      direction="rtl"
      custom-class="ai-companion-drawer"
      :before-close="handleBeforeClose"
    >
      <div class="ai-chat-box">
        <!-- 顶部 Header -->
        <div class="ai-chat-header">
          <div class="header-left">
            <div class="avatar-box">
              <span class="avatar-emoji">🧙‍♂️</span>
              <span class="status-dot"></span>
            </div>
            <div class="header-info">
              <div class="header-title">智能书童 · 小墨</div>
              <div class="header-desc">全能书库向导 (基于 Spring AI & ES8)</div>
            </div>
          </div>
          <div class="header-actions">
            <el-tooltip content="清空对话" placement="bottom">
              <el-button
                type="text"
                class="header-btn"
                @click="clearMessages"
                :disabled="loading"
              >
                🗑️
              </el-button>
            </el-tooltip>
            <el-tooltip content="收起" placement="bottom">
              <el-button type="text" class="header-btn" @click="visible = false">
                ✖️
              </el-button>
            </el-tooltip>
          </div>
        </div>

        <!-- 推荐快捷引导气泡 -->
        <div class="ai-quick-prompts">
          <div class="quick-title">💡 快捷指令：</div>
          <div class="quick-tags">
            <span
              v-for="(tag, idx) in quickTags"
              :key="idx"
              class="quick-tag-item"
              @click="sendQuickPrompt(tag.prompt)"
            >
              {{ tag.label }}
            </span>
          </div>
        </div>

        <!-- 对话消息列表 -->
        <div class="ai-messages-container" ref="messagesRef">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-row"
            :class="msg.role"
          >
            <!-- 头像 -->
            <div class="msg-avatar">
              <span v-if="msg.role === 'assistant'">🧙‍♂️</span>
              <span v-else>👤</span>
            </div>

            <!-- 消息气泡 -->
            <div class="msg-content-wrapper">
              <div class="msg-sender">
                {{ msg.role === 'assistant' ? '小墨' : (nickName || '我') }}
              </div>
              <div class="msg-bubble">
                <div class="msg-text" v-html="formatMessage(msg.content)"></div>
                <!-- 流式输出打字光标 -->
                <span
                  v-if="msg.role === 'assistant' && loading && index === messages.length - 1"
                  class="typing-cursor"
                ></span>
              </div>
            </div>
          </div>

          <!-- 思考中占位 -->
          <div v-if="loading && (!messages.length || messages[messages.length - 1].role === 'user')" class="message-row assistant">
            <div class="msg-avatar">🧙‍♂️</div>
            <div class="msg-content-wrapper">
              <div class="msg-sender">小墨</div>
              <div class="msg-bubble thinking-bubble">
                <span class="dot-flashing"></span> 正在翻阅千本藏书思考中...
              </div>
            </div>
          </div>
        </div>

        <!-- 底部输入框与操作栏 -->
        <div class="ai-input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="问小墨：'找一本主角杀伐果断的玄幻爽文'、'查新书榜' 或 '加书架' (Enter 发送)..."
            @keydown.enter.prevent="handleEnter"
            :disabled="loading"
          />
          <div class="input-bottom-bar">
            <div class="input-tips">Enter 发送 / Shift + Enter 换行</div>
            <el-button
              type="primary"
              size="small"
              class="send-btn"
              :loading="loading"
              @click="sendMessage"
              :disabled="!inputMessage.trim()"
            >
              {{ loading ? '生成中' : '发送' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { ref, reactive, toRefs, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { getToken, getNickName } from '@/utils/auth'
import { streamChatWithCompanion, addToBookshelf,
         getConversationId, newConversation, clearMemory, getChatHistory } from '@/api/ai'

export default {
  name: 'AiCompanion',
  setup() {
    const router = useRouter()
    const messagesRef = ref(null)

    const state = reactive({
      visible: false,
      loading: false,
      hasUnread: false,
      inputMessage: '',
      nickName: getNickName(),
      conversationId: getConversationId(),
      messages: [
        {
          role: 'assistant',
          content: '少侠好！我是您的专属智能书童小墨 🧙‍♂️。\n我可以帮您：\n1. 🔍 **智能找书**：自然语言理解您的找书偏好\n2. 🏆 **查排行榜**：点击榜、新书榜、更新榜一览无余\n3. 💡 **个性化推荐**：分析您的书架历史，挖掘相似好书\n4. 📥 **一键加书架**：告诉我想加哪本书，小墨为您自动入库！'
        }
      ],
      quickTags: [
        { label: '🏆 点击榜前五', prompt: '帮我看下全站点击榜前五的小说' },
        { label: '💡 推荐相似好书', prompt: '根据我书架读过的书，给我推荐几本类似的' },
        { label: '🔍 杀伐果断爽文', prompt: '想找一本主角杀伐果断、在修真界种田的小说' },
        { label: '🆕 最新新书榜', prompt: '最近有什么最新入库的新书？' }
      ]
    })

    // 检查登录状态
    const checkLogin = () => {
      const token = getToken()
      if (!token) {
        ElMessageBox.confirm(
          '智能书童为登录会员专属特权。\n登录后即可享受【AI 智能找书】、【自动加书架】与【个人书架相似推荐】！',
          '提示：需先登录账号',
          {
            confirmButtonText: '立即登录',
            cancelButtonText: '稍后再说',
            type: 'info',
            center: true
          }
        ).then(() => {
          router.push({ name: 'login' })
        }).catch(() => {})
        return false
      }
      return true
    }

    // 打开/收起聊天窗口
    const toggleChat = () => {
      if (!state.visible) {
        if (!checkLogin()) return
        state.visible = true
        state.hasUnread = false
        scrollToBottom()
      } else {
        state.visible = false
      }
    }

    // 外部调用打开方法 (比如从导航栏调用)
    const openChat = () => {
      if (!checkLogin()) return
      state.visible = true
      scrollToBottom()
    }

    // 发送快捷指令
    const sendQuickPrompt = (prompt) => {
      if (state.loading) return
      state.inputMessage = prompt
      sendMessage()
    }

    // 滚动至最新消息
    const scrollToBottom = () => {
      nextTick(() => {
        if (messagesRef.value) {
          messagesRef.value.scrollTop = messagesRef.value.scrollHeight
        }
      })
    }

    // 回车发送
    const handleEnter = (e) => {
      if (e.shiftKey) {
        state.inputMessage += '\n'
      } else {
        sendMessage()
      }
    }

    // 发送消息
    const sendMessage = async () => {
      const text = state.inputMessage.trim()
      if (!text || state.loading) return

      if (!checkLogin()) return

      // 添加用户消息
      state.messages.push({
        role: 'user',
        content: text
      })
      state.inputMessage = ''
      state.loading = true
      scrollToBottom()

      // 创建 AI 占位消息
      const assistantMsg = reactive({
        role: 'assistant',
        content: ''
      })
      state.messages.push(assistantMsg)

      try {
        await streamChatWithCompanion(
          text,
          state.conversationId,
          (chunk) => {
            assistantMsg.content += chunk
            scrollToBottom()
          },
          () => {
            state.loading = false
            scrollToBottom()
          },
          (error) => {
            state.loading = false
            assistantMsg.content += '\n\n*(哎呀，小墨似乎遇到了点小问题，请稍后再试)*'
            ElMessage.error(error.message || '请求失败')
            scrollToBottom()
          }
        )
      } catch (err) {
        state.loading = false
        ElMessage.error('连接失败，请检查网络或后端服务')
      }
    }

    // 清空消息：清服务端记忆 + 换新会话ID + 重置本地
    const clearMessages = async () => {
      if (state.loading) return
      try {
        await clearMemory(state.conversationId)
      } catch (e) {
        // 忽略清空失败，仍继续开启新会话
      }
      state.conversationId = newConversation()
      state.messages = [
        {
          role: 'assistant',
          content: '对话已清空，已开启新的一段对话。少侠，今天想了解什么题材的小说？随时问我吧！'
        }
      ]
    }

    // 格式化富文本 (Markdown 与小说书名/排版优化)
    const formatMessage = (content) => {
      if (!content) return ''
      
      // 1. 转义 HTML 特殊字符
      let text = content
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')

      // 2. 智能清理问号乱码残留 (例如 ??、??️ 等)
      text = text.replace(/\?{2,}️?/g, '')

      // 3. 处理分割线 ---
      text = text.replace(/^[ \t]*---[ \t]*$/gm, '<hr class="ai-hr"/>')

      // 4. 处理标题
      text = text.replace(/^###[ \t]+(.*?)$/gm, '<div class="ai-h3">$1</div>')
      text = text.replace(/^##[ \t]+(.*?)$/gm, '<div class="ai-h2">$1</div>')

      // 5. 处理粗体
      text = text.replace(/\*\*(.*?)\*\*/g, '<strong class="ai-bold">$1</strong>')

      // 6. 处理书名标签
      text = text.replace(/《([^》]+)》/g, '<span class="ai-book-tag">《$1》</span>')

      // 7. 处理列表项 (- item 或 * item 或 • item)
      text = text.replace(/^[ \t]*[-*•][ \t]+(.*?)$/gm, '<div class="ai-list-item"><span class="ai-dot">•</span><span class="ai-list-text">$1</span></div>')

      // 8. 换行处理：避免在 div / hr 等块级元素周围插入多余的 <br/>
      text = text.replace(/(<\/div>|<hr class="ai-hr"\/>)\n/g, '$1')
      text = text.replace(/\n(<div class="ai-list-item">|<div class="ai-h[23]">|<hr class="ai-hr"\/>)/g, '$1')
      text = text.replace(/\n/g, '<br/>')

      return text
    }

    const handleBeforeClose = (done) => {
      done()
    }

    // 刷新回显：登录态下拉取当前会话历史
    const loadHistory = async () => {
      const token = getToken()
      if (!token) return
      try {
        const resp = await getChatHistory(state.conversationId)
        const history = resp && resp.data ? resp.data : []
        if (history.length > 0) {
          state.messages = history.map(m => ({ role: m.role, content: m.content }))
          scrollToBottom()
        }
      } catch (e) {
        // 回显失败不影响使用，保留欢迎语
      }
    }

    // 全局事件监听 (支持从任何组件呼出 AI 书童) + 历史回显
    onMounted(() => {
      window.addEventListener('open-ai-companion', openChat)
      loadHistory()
    })

    return {
      ...toRefs(state),
      messagesRef,
      toggleChat,
      openChat,
      sendQuickPrompt,
      handleEnter,
      sendMessage,
      clearMessages,
      formatMessage,
      handleBeforeClose
    }
  }
}
</script>

<style scoped>
/* 悬浮按钮样式 */
.ai-floating-btn {
  position: fixed;
  bottom: 80px;
  right: 32px;
  z-index: 2000;
  cursor: pointer;
  user-select: none;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.ai-btn-inner {
  display: flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, #ff7a18 0%, #af002d 100%);
  color: #fff;
  padding: 10px 16px;
  border-radius: 24px;
  box-shadow: 0 4px 16px rgba(255, 122, 24, 0.4);
  font-size: 14px;
  font-weight: bold;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.ai-floating-btn:hover .ai-btn-inner {
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 6px 20px rgba(255, 122, 24, 0.6);
}

.ai-icon {
  font-size: 18px;
}

/* 呼吸光环 */
.pulse-ring {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 24px;
  border: 2px solid rgba(255, 122, 24, 0.6);
  animation: pulse-animation 2.2s infinite;
  pointer-events: none;
}

@keyframes pulse-animation {
  0% { transform: scale(1); opacity: 0.8; }
  70% { transform: scale(1.15); opacity: 0; }
  100% { transform: scale(1.15); opacity: 0; }
}

/* 抽屉整体容器 */
.ai-chat-box {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f8f9fc;
}

/* 顶部 Header */
.ai-chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  background: #fff;
  border-bottom: 1px solid #eef0f5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-box {
  position: relative;
  width: 38px;
  height: 38px;
  background: #fff4ec;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: inset 0 0 0 1px rgba(255, 122, 24, 0.2);
}

.status-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  background: #10b981;
  border-radius: 50%;
  border: 2px solid #fff;
}

.header-title {
  font-size: 15px;
  font-weight: bold;
  color: #1f2937;
}

.header-desc {
  font-size: 11px;
  color: #6b7280;
}

.header-btn {
  font-size: 16px;
  color: #9ca3af;
  padding: 4px;
}

.header-btn:hover {
  color: #ff7a18;
}

/* 快捷指令区 */
.ai-quick-prompts {
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #edf0f5;
}

.quick-title {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
}

.quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.quick-tag-item {
  font-size: 12px;
  background: #f3f4f6;
  color: #4b5563;
  padding: 4px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-tag-item:hover {
  background: #fff0e6;
  color: #ff7a18;
}

/* 消息流区域 */
.ai-messages-container {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-row {
  display: flex;
  gap: 10px;
  max-width: 92%;
}

.message-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-row.assistant {
  align-self: flex-start;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.message-row.assistant .msg-avatar {
  background: #fff0e6;
}

.msg-content-wrapper {
  display: flex;
  flex-direction: column;
}

.message-row.user .msg-content-wrapper {
  align-items: flex-end;
}

.msg-sender {
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13.5px;
  line-height: 1.6;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.message-row.assistant .msg-bubble {
  background: #fff;
  color: #24292f;
  border: 1px solid #e5e7eb;
  border-top-left-radius: 2px;
}

.message-row.user .msg-bubble {
  background: #ff7a18;
  color: #fff;
  border-top-right-radius: 2px;
}

/* 打字机光标 */
.typing-cursor {
  display: inline-block;
  width: 6px;
  height: 14px;
  background: #ff7a18;
  margin-left: 4px;
  vertical-align: middle;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.thinking-bubble {
  color: #6b7280;
  font-style: italic;
}

/* 底部输入框 */
.ai-input-wrapper {
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #edf0f5;
}

.input-bottom-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.input-tips {
  font-size: 11px;
  color: #9ca3af;
}

.send-btn {
  background: #ff7a18;
  border-color: #ff7a18;
  font-weight: bold;
}

.send-btn:hover {
  background: #e66a12;
  border-color: #e66a12;
}

/* AI 消息内容富文本渲染 */
.msg-bubble :deep(.ai-hr) {
  border: none;
  border-top: 1px dashed #e2e8f0;
  margin: 10px 0;
}

.msg-bubble :deep(.ai-h2) {
  font-size: 14.5px;
  font-weight: bold;
  color: #1e293b;
  margin: 8px 0 4px 0;
}

.msg-bubble :deep(.ai-h3) {
  font-size: 13.5px;
  font-weight: bold;
  color: #334155;
  margin: 6px 0 3px 0;
}

.msg-bubble :deep(.ai-list-item) {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 2px 0;
  line-height: 1.55;
}

.msg-bubble :deep(.ai-dot) {
  color: #ff7a18;
  font-weight: bold;
  flex-shrink: 0;
}

.msg-bubble :deep(.ai-list-text) {
  flex: 1;
}

.msg-bubble :deep(.ai-bold) {
  font-weight: 600;
  color: #1e293b;
}

.msg-bubble :deep(.ai-book-tag) {
  color: #ea580c;
  font-weight: 600;
  background: #fff7ed;
  padding: 1px 5px;
  border-radius: 4px;
  border: 1px solid #ffedd5;
  display: inline-block;
  margin: 0 1px;
}
</style>
