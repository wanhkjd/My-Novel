<template>
  <div v-loading="loading" class="container catalog-page">
    <div class="catalog-head">
      <h3 class="book-name">{{ book?.bookName }}</h3>
      <span class="total">共 {{ chapters.length }} 章</span>
    </div>
    <div class="chapter-grid">
      <div v-for="c in chapters" :key="c.id" class="chapter-item" @click="read(c.id)">
        <span class="text-ellipsis">{{ c.chapterName }}</span>
        <el-icon v-if="c.isVip === 1" color="#b5352b"><Lock /></el-icon>
      </div>
    </div>
    <EmptyBlock v-if="!loading && !chapters.length" text="暂无章节" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listChapters, getBookById } from '@/api/book'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const route = useRoute(), router = useRouter()
const bookId = route.params.id
const chapters = ref([]), book = ref(null), loading = ref(false)
const read = (cid) => router.push(`/read/${cid}`)
onMounted(async () => {
  loading.value = true
  try {
    getBookById(bookId).then(r => book.value = r.data).catch(() => {})
    const { data } = await listChapters(bookId); chapters.value = data || []
  } catch (e) {} finally { loading.value = false }
})
</script>

<style scoped>
.catalog-page { padding-top: var(--sp-5); }
.catalog-head { display: flex; align-items: baseline; gap: 16px; margin-bottom: var(--sp-4); }
.book-name { font-size: 22px; }
.total { color: var(--text-3); font-size: 13px; }
.chapter-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--sp-2); }
.chapter-item { display: flex; justify-content: space-between; align-items: center; gap: 6px; padding: 10px 14px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-sm); cursor: pointer; font-size: 14px; }
.chapter-item:hover { color: var(--brand); border-color: var(--brand); }
@media (max-width: 760px) { .chapter-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
