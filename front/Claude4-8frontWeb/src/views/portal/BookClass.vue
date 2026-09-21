<template>
  <div class="container class-page">
    <h3 class="section-title">小说分类</h3>
    <el-tabs v-model="dir" @tab-change="loadCats">
      <el-tab-pane label="男频" :name="0" />
      <el-tab-pane label="女频" :name="1" />
    </el-tabs>
    <div v-loading="loading" class="cat-grid">
      <div v-for="c in cats" :key="c.id" class="cat-item card hover-lift" @click="go(c)">
        <span class="cat-name">{{ c.name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCategoryStore } from '@/stores/category'
const router = useRouter()
const store = useCategoryStore()
const dir = ref(0), cats = ref([]), loading = ref(false)
async function loadCats() {
  loading.value = true
  try { cats.value = await store.load(dir.value) } finally { loading.value = false }
}
const go = (c) => router.push({ name: 'search', query: { workDirection: dir.value, categoryId: c.id } })
onMounted(loadCats)
</script>

<style scoped>
.class-page { padding-top: var(--sp-5); }
.cat-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: var(--sp-3); margin-top: var(--sp-4); }
.cat-item { padding: 20px; text-align: center; cursor: pointer; }
.cat-name { font-family: var(--font-serif); font-size: 16px; }
@media (max-width: 760px) { .cat-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
