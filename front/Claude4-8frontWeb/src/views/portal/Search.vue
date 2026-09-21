<template>
  <div class="container search-page">
    <div class="filter-bar card">
      <el-input v-model="cond.keyword" placeholder="书名 / 作者关键字" class="kw" @keyup.enter="doSearch" clearable />
      <el-select v-model="cond.workDirection" placeholder="频道" clearable @change="onDirChange" style="width:120px">
        <el-option label="男频" :value="0" /><el-option label="女频" :value="1" />
      </el-select>
      <el-select v-model="cond.categoryId" placeholder="分类" clearable style="width:140px">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="cond.bookStatus" placeholder="状态" clearable style="width:120px">
        <el-option label="连载中" :value="0" /><el-option label="已完结" :value="1" />
      </el-select>
      <el-select v-model="cond.isVip" placeholder="收费" clearable style="width:120px">
        <el-option label="免费" :value="0" /><el-option label="收费" :value="1" />
      </el-select>
      <el-button type="primary" @click="doSearch">搜索</el-button>
    </div>

    <div v-loading="loading" class="result">
      <template v-if="list.length">
        <div class="result-list">
          <BookCard v-for="b in list" :key="b.id" :book="b" horizontal />
        </div>
        <Pagination :total="total" :page-num="cond.pageNum" :page-size="cond.pageSize"
                    @update:pageNum="onPage" />
      </template>
      <EmptyBlock v-else-if="!loading" text="没有找到符合条件的书籍" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { searchBooks } from '@/api/search'
import { useCategoryStore } from '@/stores/category'
import BookCard from '@/components/common/BookCard.vue'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'

const route = useRoute()
const categoryStore = useCategoryStore()
const categories = ref([])
const list = ref([]), total = ref(0), loading = ref(false)
const cond = reactive({
  keyword: route.query.keyword || '',
  workDirection: route.query.workDirection !== undefined ? Number(route.query.workDirection) : null,
  categoryId: route.query.categoryId !== undefined ? Number(route.query.categoryId) : null,
  bookStatus: null, isVip: null, pageNum: 1, pageSize: 10
})

async function onDirChange(v) {
  cond.categoryId = null
  categories.value = v === null || v === undefined ? [] : await categoryStore.load(v)
}
async function doSearch() {
  cond.pageNum = 1
  await load()
}
async function onPage(p) { cond.pageNum = p; await load() }
async function load() {
  loading.value = true
  try {
    const params = {}
    Object.entries(cond).forEach(([k, v]) => { if (v !== null && v !== '' && v !== undefined) params[k] = v })
    const { data } = await searchBooks(params)
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally { loading.value = false }
}
onMounted(async () => {
  if (cond.workDirection !== null) categories.value = await categoryStore.load(cond.workDirection)
  await load()
})
</script>

<style scoped>
.search-page { padding-top: var(--sp-5); }
.filter-bar { display: flex; gap: var(--sp-3); padding: var(--sp-4); flex-wrap: wrap; align-items: center; }
.filter-bar .kw { flex: 1; min-width: 220px; }
.result-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sp-4); margin-top: var(--sp-4); }
@media (max-width: 760px) { .result-list { grid-template-columns: 1fr; } }
</style>
