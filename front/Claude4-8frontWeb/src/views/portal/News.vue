<template>
  <div v-loading="loading" class="container news-page">
    <article v-if="news" class="news card">
      <h1 class="title">{{ news.title }}</h1>
      <div class="meta">
        <span>{{ news.sourceName }}</span>
        <span>{{ formatTime(news.updateTime) }}</span>
        <span v-if="news.categoryName" class="tag">{{ news.categoryName }}</span>
      </div>
      <div class="content" v-html="news.content"></div>
    </article>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getNewsById } from '@/api/news'
import { formatTime } from '@/utils/format'
const route = useRoute()
const news = ref(null), loading = ref(false)
onMounted(async () => {
  loading.value = true
  try { const { data } = await getNewsById(route.params.id); news.value = data }
  catch (e) {} finally { loading.value = false }
})
</script>

<style scoped>
.news-page { padding-top: var(--sp-5); max-width: 820px; }
.news { padding: var(--sp-6); }
.title { font-size: 26px; text-align: center; margin-bottom: var(--sp-4); }
.meta { display: flex; gap: 16px; justify-content: center; color: var(--text-3); font-size: 13px; padding-bottom: var(--sp-4); border-bottom: 1px solid var(--border); }
.meta .tag { color: var(--brand); }
.content { padding-top: var(--sp-4); line-height: 1.9; font-size: 16px; }
.content :deep(p) { margin: 0 0 1em; }
</style>
