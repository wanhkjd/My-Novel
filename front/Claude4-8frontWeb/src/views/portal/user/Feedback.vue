<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">意见反馈</h3>
    <div class="fb-form card">
      <el-input v-model="content" type="textarea" :rows="4" maxlength="512" show-word-limit
                placeholder="请描述你的问题或建议（5-512 字）" />
      <el-button type="primary" @click="submit">提交反馈</el-button>
    </div>
    <div class="fb-list">
      <div v-for="f in list" :key="f.id" class="fb-item card">
        <div class="fb-content">{{ f.content }}</div>
        <div class="fb-foot">
          <span>{{ formatTime(f.createTime) }}</span>
          <el-button size="small" text type="danger" @click="remove(f.id)">删除</el-button>
        </div>
      </div>
      <EmptyBlock v-if="!loading && !list.length" text="还没有反馈记录" />
    </div>
    <Pagination :total="total" :page-num="page.pageNum" :page-size="page.pageSize" @update:pageNum="onPage" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { submitFeedback, listFeedbacks, deleteFeedback } from '@/api/user'
import { formatTime } from '@/utils/format'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const content = ref(''), list = ref([]), total = ref(0), loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10 })
async function submit() {
  if (content.value.trim().length < 5) { ElMessage.warning('反馈至少 5 个字'); return }
  try { await submitFeedback(content.value); ElMessage.success('反馈已提交'); content.value = ''; load() } catch (e) {}
}
async function remove(id) { try { await deleteFeedback(id); ElMessage.success('已删除'); load() } catch (e) {} }
async function load() {
  loading.value = true
  try { const { data } = await listFeedbacks({ ...page }); list.value = data.list || []; total.value = data.total || 0 }
  catch (e) {} finally { loading.value = false }
}
function onPage(p) { page.pageNum = p; load() }
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.fb-form { padding: var(--sp-4); display: flex; flex-direction: column; gap: 12px; align-items: flex-end; margin-bottom: var(--sp-4); }
.fb-list { display: flex; flex-direction: column; gap: var(--sp-3); }
.fb-item { padding: var(--sp-4); }
.fb-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; color: var(--text-3); font-size: 12px; }
</style>
