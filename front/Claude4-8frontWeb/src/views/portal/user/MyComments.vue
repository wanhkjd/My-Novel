<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">我的评论</h3>
    <template v-if="list.length">
      <div class="comment-list">
        <div v-for="c in list" :key="c.id" class="comment-card card">
          <img class="c-cover" :src="cover(c.commentBookPic)" @error="onErr" />
          <div class="c-main">
            <div class="c-book">{{ c.commentBook }}</div>
            <div class="c-content">{{ c.commentContent }}</div>
            <div class="c-foot">
              <span>{{ formatTime(c.commentTime) }}</span>
              <span class="audit" :class="'a' + c.auditStatus">{{ auditText(c.auditStatus) }}</span>
            </div>
          </div>
        </div>
      </div>
      <Pagination :total="total" :page-num="page.pageNum" :page-size="page.pageSize" @update:pageNum="onPage" />
    </template>
    <EmptyBlock v-else-if="!loading" text="还没有发表过评论" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listComments } from '@/api/user'
import { formatTime } from '@/utils/format'
import Pagination from '@/components/common/Pagination.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
const list = ref([]), total = ref(0), loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10 })
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="60" height="80"><rect width="60" height="80" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const auditText = (s) => (s === 1 ? '已通过' : s === 2 ? '未通过' : '待审核')
async function load() {
  loading.value = true
  try { const { data } = await listComments({ ...page }); list.value = data.list || []; total.value = data.total || 0 }
  catch (e) {} finally { loading.value = false }
}
function onPage(p) { page.pageNum = p; load() }
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.comment-list { display: flex; flex-direction: column; gap: var(--sp-3); }
.comment-card { display: flex; gap: var(--sp-3); padding: var(--sp-4); }
.c-cover { width: 60px; height: 80px; object-fit: cover; border-radius: var(--radius-sm); }
.c-main { flex: 1; }
.c-book { font-weight: 600; }
.c-content { color: var(--text-2); margin: 6px 0; }
.c-foot { display: flex; gap: 16px; font-size: 12px; color: var(--text-3); }
.audit.a1 { color: #67c23a; } .audit.a2 { color: var(--brand); } .audit.a0 { color: #e6a23c; }
</style>
