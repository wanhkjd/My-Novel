<template>
  <div class="ai-wrap">
    <div class="ai-fab" :class="{ open: visible }" @click="toggle">
      <span class="fab-icon">🖋️</span>
      <span class="fab-text">书童</span>
    </div>

    <el-drawer v-model="visible" :with-header="false" size="420px" direction="rtl">
      <div class="chat-box">
        <div class="chat-head">
          <div class="head-left">
            <span class="avatar">🧙</span>
            <div>
              <div class="h-title">智能书童 · 小墨</div>
              <div class="h-desc">找书 / 榜单 / 推荐，问我就好</div>
            </div>
          </div>
          <div class="head-actions">
            <el-button size="small" text @click="startNew" :disabled="loading">新对话</el-button>
            <el-button size="small" text @click="visible = false">收起</el-button>
          </div>
        </div>

        <!-- 书童全部接口(对话/历史/推荐)后端均需登录，未登录时引导登录 -->
        <template v-if="userStore.isLoggedIn">
          <div class="quick">
            <span v-for="(q, i) in quicks" :key="i" class="quick-tag" @click="send(q)">{{ q }}</span>
          </div>

          <div class="msg-list" ref="listRef">
            <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
              <span class="m-avatar">{{ m.role === 'assistant' ? '🧙' : '👤' }}</span>
              <div class="m-bubble" v-html="renderMd(m.content)"></div>
            </div>
            <div v-if="thinking" class="msg assistant">
              <span class="m-avatar">🧙</span>
              <div class="m-bubble typing">正在翻阅藏书……</div>
            </div>
          </div>

          <div v-if="recs.length" class="recs">
            <div class="recs-title">📖 为你推荐</div>
            <div v-for="r in recs" :key="r.bookId" class="rec-item" @click="goBook(r.bookId)">
              <span class="rec-name">{{ r.bookName }}</span>
              <span class="rec-reason">{{ r.recommendReason }}</span>
            </div>
          </div>

          <div class="chat-input">
            <el-input v-model="input" type="textarea" :rows="2" resize="none"
                      placeholder="输入你想找的书，回车发送" @keyup.enter.exact.prevent="send()" />
            <el-button type="primary" :disabled="loading || !input.trim()" @click="send()">发送</el-button>
          </div>
        </template>

        <div v-else class="ai-guest">
          <p class="guest-emoji">🔒</p>
          <p class="guest-tip">登录后即可召唤智能书童</p>
          <p class="guest-sub">帮你找书、看榜单、按偏好推荐好书</p>
          <el-button type="primary" @click="goLogin">去登录</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getConversationId, newConversation, streamChat,
  getChatHistory, clearMemory, recommendSimilar
} from '@/api/ai'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const visible = ref(false)
const input = ref(''), loading = ref(false), thinking = ref(false)
const messages = ref([]), recs = ref([])
const listRef = ref()
const convId = ref(getConversationId())
const quicks = ['推荐几本修仙小说', '最近更新榜有什么', '找一本轻松搞笑的', '有什么完结的好书']

function renderMd(text) {
  if (!text) return ''
  return String(text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\n/g, '<br/>')
}
async function scrollToBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}
function goBook(id) { visible.value = false; router.push(`/book/${id}`) }
function goLogin() { visible.value = false; router.push('/login') }

async function send(preset) {
  if (!userStore.isLoggedIn) return
  const text = (preset ?? input.value).trim()
  if (!text || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '' })
  const idx = messages.value.length - 1
  loading.value = true; thinking.value = true
  await scrollToBottom()
  await streamChat(text, convId.value, {
    onChunk: (chunk) => {
      thinking.value = false
      messages.value[idx].content += chunk
      scrollToBottom()
    },
    onDone: () => { loading.value = false; thinking.value = false },
    onError: (err) => {
      loading.value = false; thinking.value = false
      messages.value[idx].content = messages.value[idx].content || ('❌ ' + (err.message || '对话失败'))
    }
  })
}

async function startNew() {
  try { await clearMemory(convId.value) } catch (e) {}
  convId.value = newConversation()
  messages.value = []
}

async function loadHistory() {
  if (!userStore.isLoggedIn) return
  try {
    const { data } = await getChatHistory(convId.value)
    if (Array.isArray(data)) messages.value = data.map(m => ({ role: m.role, content: m.content }))
    scrollToBottom()
  } catch (e) {}
}
async function loadRecs() {
  if (!userStore.isLoggedIn) return
  try { const { data } = await recommendSimilar(4); recs.value = data || [] } catch (e) {}
}

const toggle = () => { visible.value = !visible.value }
// 书童所有接口后端均需登录：仅登录态才拉取历史与推荐
watch(visible, (v) => { if (v && userStore.isLoggedIn) { loadHistory(); loadRecs() } })
</script>

<style scoped>
.ai-fab { position: fixed; right: 28px; bottom: 28px; z-index: 60; display: flex; flex-direction: column; align-items: center; gap: 2px; width: 60px; height: 60px; justify-content: center; background: var(--brand); color: #fff; border-radius: 50%; box-shadow: var(--shadow-md); cursor: pointer; transition: transform .2s; }
.ai-fab:hover { transform: scale(1.08); }
.fab-icon { font-size: 20px; }
.fab-text { font-size: 11px; }
.chat-box { display: flex; flex-direction: column; height: 100%; }
.chat-head { display: flex; justify-content: space-between; align-items: center; padding-bottom: var(--sp-3); border-bottom: 1px solid var(--border); }
.head-left { display: flex; gap: 10px; align-items: center; }
.avatar { font-size: 28px; }
.h-title { font-weight: 600; }
.h-desc { font-size: 12px; color: var(--text-3); }
.quick { display: flex; flex-wrap: wrap; gap: 6px; padding: var(--sp-3) 0; }
.quick-tag { font-size: 12px; padding: 4px 10px; background: var(--brand-soft); color: var(--brand); border-radius: 12px; cursor: pointer; }
.msg-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; padding: var(--sp-3) 0; }
.msg { display: flex; gap: 8px; }
.msg.user { flex-direction: row-reverse; }
.m-avatar { font-size: 22px; flex: 0 0 auto; }
.m-bubble { max-width: 78%; padding: 10px 12px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); font-size: 14px; line-height: 1.6; word-break: break-word; }
.msg.user .m-bubble { background: var(--brand); color: #fff; border-color: var(--brand); }
.m-bubble.typing { color: var(--text-3); }
.recs { border-top: 1px solid var(--border); padding: var(--sp-3) 0; }
.recs-title { font-size: 13px; color: var(--text-2); margin-bottom: 6px; }
.rec-item { display: flex; flex-direction: column; padding: 6px 0; cursor: pointer; }
.rec-item:hover .rec-name { color: var(--brand); }
.rec-name { font-size: 14px; font-weight: 600; }
.rec-reason { font-size: 12px; color: var(--text-3); }
.chat-input { display: flex; gap: 8px; padding-top: var(--sp-3); border-top: 1px solid var(--border); align-items: flex-end; }
.ai-guest { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; text-align: center; color: var(--text-2); }
.guest-emoji { font-size: 40px; }
.guest-tip { font-size: 15px; font-weight: 600; color: var(--text-1); }
.guest-sub { font-size: 13px; color: var(--text-3); margin-bottom: 8px; }
</style>
