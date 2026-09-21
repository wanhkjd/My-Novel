<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">我的书架</h3>
    <template v-if="list.length">
      <div class="shelf-grid">
        <div v-for="b in list" :key="b.id" class="shelf-item">
          <BookCard :book="b" />
          <el-button size="small" text type="danger" @click="remove(b.id)">移出</el-button>
        </div>
      </div>
      <Pagination :total="total" :page-num="page.pageNum" :page-size="page.pageSize" @update:pageNum="onPage" />
    </template>
    <EmptyBlock v-else-if="!loading" text="书架空空如也，去首页逛逛吧" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listBookshelf, removeBookshelf } from '@/api/user'
import BookCard from '@/components/common/BookCard.vue'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const list = ref([]), total = ref(0), loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 12 })
async function load() {
  loading.value = true
  try { const { data } = await listBookshelf({ ...page }); list.value = data.list || []; total.value = data.total || 0 }
  catch (e) {} finally { loading.value = false }
}
async function remove(id) {
  try { await removeBookshelf(id); ElMessage.success('已移出书架'); load() } catch (e) {}
}
function onPage(p) { page.pageNum = p; load() }
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.shelf-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: var(--sp-4); }
.shelf-item { display: flex; flex-direction: column; gap: 6px; align-items: center; }
@media (max-width: 900px) { .shelf-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
