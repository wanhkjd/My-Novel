<template>
  <div class="container rank-page">
    <h3 class="section-title">小说榜单</h3>
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="点击榜" name="visit" />
      <el-tab-pane label="新书榜" name="newest" />
      <el-tab-pane label="更新榜" name="update" />
    </el-tabs>
    <div v-loading="loading" class="rank-books">
      <div v-for="(b, i) in list" :key="b.id" class="rank-row card hover-lift" @click="go(b.id)">
        <span class="no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
        <img :src="cover(b.picUrl)" @error="onErr" />
        <div class="meta">
          <h4>{{ b.bookName }}</h4>
          <p class="author">{{ b.authorName }}</p>
          <p class="desc text-clamp-2">{{ b.bookDesc }}</p>
          <p class="last">最新：{{ b.lastChapterName || '-' }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listVisitRankBooks, listNewestRankBooks, listUpdateRankBooks } from '@/api/book'
const router = useRouter()
const tab = ref('visit'), list = ref([]), loading = ref(false)
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="90" height="120"><rect width="90" height="120" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const FN = { visit: listVisitRankBooks, newest: listNewestRankBooks, update: listUpdateRankBooks }
async function load() {
  loading.value = true
  try { const { data } = await FN[tab.value](); list.value = data || [] }
  catch (e) { /* ignore */ } finally { loading.value = false }
}
const go = (id) => router.push(`/book/${id}`)
onMounted(load)
</script>

<style scoped>
.rank-page { padding-top: var(--sp-5); }
.rank-books { display: flex; flex-direction: column; gap: var(--sp-3); }
.rank-row { display: flex; gap: var(--sp-4); padding: var(--sp-4); cursor: pointer; align-items: center; }
.no { font-family: var(--font-serif); font-size: 22px; width: 34px; text-align: center; color: var(--text-3); }
.no.top { color: var(--brand); }
.rank-row img { width: 90px; height: 120px; object-fit: cover; border-radius: var(--radius-sm); }
.meta { flex: 1; }
.author { color: var(--text-2); font-size: 13px; }
.desc { color: var(--text-2); font-size: 13px; margin: 6px 0; }
.last { color: var(--text-3); font-size: 12px; }
</style>
