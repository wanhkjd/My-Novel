<template>
  <div class="reader-page" :class="`reader-theme-${theme}`" :style="readerVars">
    <div class="reader-topbar">
      <div class="container topbar-inner">
        <span class="back" @click="goDetail">‹ {{ content?.bookInfo?.bookName || '返回' }}</span>
        <div class="tools">
          <el-button-group>
            <el-button size="small" @click="setTheme('day')">日间</el-button>
            <el-button size="small" @click="setTheme('sepia')">护眼</el-button>
            <el-button size="small" @click="setTheme('night')">夜间</el-button>
          </el-button-group>
          <el-button size="small" @click="fs = Math.max(14, fs - 2)">A-</el-button>
          <el-button size="small" @click="fs = Math.min(28, fs + 2)">A+</el-button>
          <el-button size="small" @click="goCatalog">目录</el-button>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="reader-content">
      <h2 class="chapter-title">{{ content?.chapterInfo?.chapterName }}</h2>
      <div class="reader-body" v-html="renderedContent"></div>
      <div class="reader-nav">
        <el-button :disabled="!hasPrev" @click="goPrev">上一章</el-button>
        <el-button @click="goCatalog">目录</el-button>
        <el-button :disabled="!hasNext" @click="goNext">下一章</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBookContent, getPreChapterId, getNextChapterId } from '@/api/book'
import '@/styles/reader.css'

const route = useRoute(), router = useRouter()
const content = ref(null), loading = ref(false)
const prevId = ref(null), nextId = ref(null)
const theme = ref(localStorage.getItem('reader_theme') || 'day')
const fs = ref(Number(localStorage.getItem('reader_fs')) || 18)

const readerVars = computed(() => ({ '--reader-fs': fs.value + 'px', '--reader-lh': 1.9 }))
const hasPrev = computed(() => prevId.value && prevId.value > 0)
const hasNext = computed(() => nextId.value && nextId.value > 0)
const renderedContent = computed(() => {
  const c = content.value?.bookContent || ''
  return c.split(/\n+/).filter(Boolean).map(p => `<p>${p}</p>`).join('')
})

const setTheme = (t) => { theme.value = t; localStorage.setItem('reader_theme', t) }
watch(fs, (v) => localStorage.setItem('reader_fs', String(v)))

const goDetail = () => { if (content.value?.bookInfo?.id) router.push(`/book/${content.value.bookInfo.id}`) }
const goCatalog = () => { if (content.value?.bookInfo?.id) router.push(`/book/${content.value.bookInfo.id}/catalog`) }
const goPrev = () => hasPrev.value && router.push(`/read/${prevId.value}`)
const goNext = () => hasNext.value && router.push(`/read/${nextId.value}`)

async function load(chapterId) {
  loading.value = true
  try {
    const { data } = await getBookContent(chapterId); content.value = data
    getPreChapterId(chapterId).then(r => prevId.value = r.data).catch(() => prevId.value = null)
    getNextChapterId(chapterId).then(r => nextId.value = r.data).catch(() => nextId.value = null)
    window.scrollTo({ top: 0 })
  } catch (e) { ElMessage.error('章节加载失败') } finally { loading.value = false }
}
watch(() => route.params.chapterId, (id) => { if (id) load(id) })
onMounted(() => load(route.params.chapterId))
</script>

<style scoped>
.reader-topbar { position: sticky; top: 0; background: rgba(255,255,255,0.02); backdrop-filter: blur(4px); border-bottom: 1px solid rgba(150,150,150,0.15); padding: 10px 0; z-index: 20; }
.topbar-inner { display: flex; justify-content: space-between; align-items: center; }
.back { cursor: pointer; font-size: 15px; }
.tools { display: flex; gap: 8px; align-items: center; }
.reader-content { padding-bottom: 60px; }
.chapter-title { text-align: center; padding-top: 40px; font-family: var(--font-serif); }
.reader-nav { display: flex; justify-content: center; gap: 16px; padding: 40px 0; }
</style>
