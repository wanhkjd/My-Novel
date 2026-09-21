<template>
  <div class="rank-panel card">
    <h4 class="rank-title">{{ title }}</h4>
    <ol class="rank-list">
      <li v-for="(b, i) in books" :key="b.id" @click="go(b.id)">
        <span class="rank-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
        <span class="rank-name text-ellipsis">{{ b.bookName }}</span>
      </li>
    </ol>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
defineProps({ title: String, books: { type: Array, default: () => [] } })
const router = useRouter()
const go = (id) => router.push(`/book/${id}`)
</script>

<style scoped>
.rank-panel { padding: var(--sp-4); }
.rank-title { font-family: var(--font-serif); font-size: 17px; margin-bottom: var(--sp-3); border-left: 4px solid var(--brand); padding-left: 10px; }
.rank-list { list-style: none; margin: 0; padding: 0; }
.rank-list li { display: flex; align-items: center; gap: 10px; padding: 7px 0; cursor: pointer; }
.rank-list li:hover .rank-name { color: var(--brand); }
.rank-no { width: 20px; text-align: center; color: var(--text-3); font-weight: 600; }
.rank-no.top { color: var(--brand); }
.rank-name { flex: 1; font-size: 14px; }
</style>
