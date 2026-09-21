<template>
  <div class="latest-news card">
    <h4 class="ln-title">最新资讯</h4>
    <ul>
      <li v-for="n in news" :key="n.id" @click="go(n.id)">
        <span class="dot"></span><span class="text-ellipsis">{{ n.title }}</span>
      </li>
    </ul>
  </div>
</template>
<script setup>
import { useRouter } from 'vue-router'
defineProps({ news: { type: Array, default: () => [] } })
const router = useRouter()
const go = (id) => router.push(`/news/${id}`)
</script>
<style scoped>
.latest-news { padding: var(--sp-4); }
.ln-title { font-family: var(--font-serif); font-size: 17px; margin-bottom: var(--sp-3); border-left: 4px solid var(--brand); padding-left: 10px; }
.latest-news ul { list-style: none; margin: 0; padding: 0; }
.latest-news li { display: flex; align-items: center; gap: 8px; padding: 6px 0; cursor: pointer; font-size: 14px; color: var(--text-2); }
.latest-news li:hover { color: var(--brand); }
.dot { width: 5px; height: 5px; border-radius: 50%; background: var(--brand); flex: 0 0 5px; }
</style>
