<template>
  <div v-loading="loading" class="container detail-page">
    <div v-if="book" class="detail-head card">
      <img class="cover" :src="cover(book.picUrl)" @error="onErr" />
      <div class="info">
        <h2 class="name">{{ book.bookName }}</h2>
        <div class="sub">
          <span>{{ book.authorName }}</span>
          <span class="tag">{{ book.categoryName }}</span>
          <span class="tag">{{ statusText }}</span>
        </div>
        <div class="stats">
          <div><b>{{ visit }}</b><span>点击</span></div>
          <div><b>{{ words }}</b><span>字数</span></div>
          <div><b>{{ book.commentCount || 0 }}</b><span>评论</span></div>
        </div>
        <p class="desc text-clamp-3">{{ book.bookDesc }}</p>
        <div class="actions">
          <el-button type="primary" size="large" @click="startRead">开始阅读</el-button>
          <el-button size="large" @click="toCatalog">目录</el-button>
          <el-button size="large" :type="inShelf ? 'success' : 'default'" @click="toggleShelf">
            {{ inShelf ? '已在书架' : '加入书架' }}
          </el-button>
        </div>
      </div>
    </div>

    <div class="detail-body">
      <div class="main-col">
        <div class="last-chapter card" v-if="lastAbout">
          <h4 class="section-title">最新章节</h4>
          <p class="lc-name" @click="readChapter(lastAbout.chapterInfo?.id)">{{ lastAbout.chapterInfo?.chapterName }}</p>
          <p class="lc-summary">{{ lastAbout.contentSummary }}</p>
          <p class="lc-total">共 {{ lastAbout.chapterTotal }} 章</p>
        </div>

        <div class="comments card">
          <h4 class="section-title">最新评论（{{ commentData.commentTotal || 0 }}）</h4>
          <div v-if="userStore.isLoggedIn" class="comment-form">
            <el-input v-model="newComment" type="textarea" :rows="3" maxlength="512"
                      placeholder="写下你的评论（10-512 字）" />
            <el-button type="primary" @click="submitComment">发表评论</el-button>
          </div>
          <p v-else class="login-tip"><router-link to="/login">登录</router-link> 后可发表评论</p>
          <div v-if="commentData.comments && commentData.comments.length" class="comment-list">
            <div v-for="c in commentData.comments" :key="c.id" class="comment-item">
              <img class="c-avatar" :src="cover(c.commentUserPhoto)" @error="onErr" />
              <div class="c-body">
                <div class="c-user">{{ c.commentUser }}</div>
                <div class="c-content">{{ c.commentContent }}</div>
                <div class="c-time">{{ formatTime(c.commentTime) }}</div>
              </div>
            </div>
          </div>
          <EmptyBlock v-else text="还没有评论，来抢沙发" />
        </div>
      </div>

      <aside class="rec-col">
        <h4 class="section-title">相关推荐</h4>
        <div class="rec-list">
          <BookCard v-for="b in recBooks" :key="b.id" :book="b" />
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getBookById, getLastChapterAbout, listRecBooks, listNewestComments, addVisitCount
} from '@/api/book'
import {
  getBookshelfStatus, addBookshelf, removeBookshelf, comment as postComment
} from '@/api/user'
import { useUserStore } from '@/stores/user'
import { formatWordCount, formatVisit, bookStatusText, formatTime } from '@/utils/format'
import BookCard from '@/components/common/BookCard.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'

const route = useRoute(), router = useRouter()
const userStore = useUserStore()
const bookId = route.params.id
const book = ref(null), lastAbout = ref(null), recBooks = ref([])
const commentData = ref({ commentTotal: 0, comments: [] })
const inShelf = ref(false), loading = ref(false), newComment = ref('')

const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="160" height="210"><rect width="160" height="210" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const statusText = computed(() => bookStatusText(book.value?.bookStatus))
const words = computed(() => formatWordCount(book.value?.wordCount))
const visit = computed(() => formatVisit(book.value?.visitCount))

const startRead = () => { if (book.value?.firstChapterId) router.push(`/read/${book.value.firstChapterId}`) }
const toCatalog = () => router.push(`/book/${bookId}/catalog`)
const readChapter = (cid) => { if (cid) router.push(`/read/${cid}`) }

async function toggleShelf() {
  if (!userStore.isLoggedIn) { router.push({ name: 'login', query: { redirect: route.fullPath } }); return }
  try {
    if (inShelf.value) { await removeBookshelf(bookId); inShelf.value = false; ElMessage.success('已移出书架') }
    else { await addBookshelf(bookId); inShelf.value = true; ElMessage.success('已加入书架') }
  } catch (e) { /* ignore */ }
}
async function submitComment() {
  if (newComment.value.trim().length < 10) { ElMessage.warning('评论至少 10 个字'); return }
  try {
    await postComment({ bookId: Number(bookId), commentContent: newComment.value })
    ElMessage.success('评论成功'); newComment.value = ''
    const { data } = await listNewestComments(bookId); commentData.value = data || { comments: [] }
  } catch (e) { /* ignore */ }
}

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await getBookById(bookId); book.value = data
    addVisitCount(bookId).catch(() => {})
    getLastChapterAbout(bookId).then(r => lastAbout.value = r.data).catch(() => {})
    listRecBooks(bookId).then(r => recBooks.value = r.data || []).catch(() => {})
    listNewestComments(bookId).then(r => commentData.value = r.data || { comments: [] }).catch(() => {})
    if (userStore.isLoggedIn) {
      getBookshelfStatus(bookId).then(r => inShelf.value = r.data === 1).catch(() => {})
    }
  } catch (e) { /* ignore */ } finally { loading.value = false }
})
</script>

<style scoped>
.detail-page { padding-top: var(--sp-5); }
.detail-head { display: flex; gap: var(--sp-5); padding: var(--sp-5); }
.detail-head .cover { width: 160px; height: 210px; object-fit: cover; border-radius: var(--radius-sm); }
.info { flex: 1; }
.name { font-size: 26px; margin-bottom: 8px; }
.sub { display: flex; gap: 12px; align-items: center; color: var(--text-2); margin-bottom: var(--sp-3); }
.sub .tag { background: var(--brand-soft); color: var(--brand); padding: 2px 10px; border-radius: 12px; font-size: 12px; }
.stats { display: flex; gap: var(--sp-6); margin: var(--sp-3) 0; }
.stats b { font-family: var(--font-serif); font-size: 20px; color: var(--brand); display: block; }
.stats span { font-size: 12px; color: var(--text-3); }
.desc { color: var(--text-2); margin-bottom: var(--sp-4); }
.detail-body { display: grid; grid-template-columns: 1fr 260px; gap: var(--sp-5); margin-top: var(--sp-5); }
.main-col { display: flex; flex-direction: column; gap: var(--sp-4); }
.last-chapter, .comments { padding: var(--sp-4); }
.lc-name { color: var(--brand); cursor: pointer; font-size: 15px; }
.lc-summary { color: var(--text-2); font-size: 13px; margin: 8px 0; }
.lc-total { color: var(--text-3); font-size: 12px; }
.comment-form { display: flex; flex-direction: column; gap: 10px; align-items: flex-end; margin-bottom: var(--sp-4); }
.login-tip { color: var(--text-3); margin-bottom: var(--sp-3); }
.login-tip a { color: var(--brand); }
.comment-item { display: flex; gap: 12px; padding: 12px 0; border-top: 1px solid var(--border); }
.c-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.c-user { font-weight: 600; font-size: 14px; }
.c-content { color: var(--text); margin: 4px 0; }
.c-time { color: var(--text-3); font-size: 12px; }
.rec-list { display: flex; flex-direction: column; gap: var(--sp-3); }
@media (max-width: 900px) { .detail-body { grid-template-columns: 1fr; } }
</style>
