<template>
  <div class="book-card hover-lift" :class="{ horizontal }" @click="goDetail">
    <div class="cover">
      <img :src="coverUrl" :alt="book.bookName" @error="onImgError" />
    </div>
    <div class="info">
      <h4 class="book-name text-ellipsis" v-html="renderHighlight(book.bookName)"></h4>
      <p class="author text-ellipsis" v-html="renderHighlight(book.authorName)"></p>
      <p v-if="horizontal" class="desc text-clamp-2">{{ toPlainText(book.bookDesc) }}</p>
      <div v-if="horizontal" class="meta">
        <span class="tag">{{ statusText }}</span>
        <span v-if="book.wordCount">{{ words }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatWordCount, bookStatusText, toPlainText, renderHighlight } from '@/utils/format'

const props = defineProps({
  book: { type: Object, required: true },
  horizontal: { type: Boolean, default: false }
})
const router = useRouter()
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="120" height="160"><rect width="120" height="160" fill="%23eae4db"/><text x="60" y="80" font-size="12" fill="%239a9a9a" text-anchor="middle">封面</text></svg>'
const coverUrl = computed(() => props.book.picUrl ? IMG + props.book.picUrl : FALLBACK)
const statusText = computed(() => bookStatusText(props.book.bookStatus))
const words = computed(() => formatWordCount(props.book.wordCount))
const goDetail = () => router.push(`/book/${props.book.id ?? props.book.bookId}`)
const onImgError = (e) => { e.target.src = FALLBACK }
</script>

<style scoped>
.book-card { display: flex; flex-direction: column; cursor: pointer; background: var(--surface); border-radius: var(--radius); overflow: hidden; border: 1px solid var(--border); }
.book-card .cover { aspect-ratio: 3/4; overflow: hidden; }
.book-card .cover img { width: 100%; height: 100%; object-fit: cover; }
.book-card .info { padding: var(--sp-3); }
.book-name { font-size: 15px; margin-bottom: 4px; }
.author { color: var(--text-2); font-size: 12px; }
.book-card.horizontal { flex-direction: row; }
.book-card.horizontal .cover { width: 120px; flex: 0 0 120px; }
.desc { color: var(--text-2); font-size: 13px; margin: 8px 0; }
.meta { display: flex; gap: 12px; font-size: 12px; color: var(--text-3); }
.meta .tag { color: var(--brand); }
/* v-html 内容不受 scoped 约束，用 :deep 命中高亮标签 */
:deep(em.hl) { color: #c0392b; font-style: normal; font-weight: 600; }
</style>
