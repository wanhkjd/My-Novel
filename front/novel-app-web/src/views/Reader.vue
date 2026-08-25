<template>
  <div class="page reader-page no-tab" :class="themeClass">
    <AppHeader :title="chapterName" back compact />
    <main class="reader-shell" :style="{ '--reader-font-size': fontSize + 'px' }">
      <h1 class="reader-title">{{ chapterName }}</h1>
      <p class="reader-info">
        {{ bookInfo.categoryName || '' }}
        <span v-if="bookInfo.authorName"> · {{ bookInfo.authorName }}</span>
        <span v-if="chapterInfo.chapterWordCount"> · {{ chapterInfo.chapterWordCount }} 字</span>
      </p>
      <article class="reader-content" v-html="content"></article>
    </main>

    <nav class="reader-toolbar" aria-label="阅读操作">
      <button type="button" @click="previous">上一章</button>
      <router-link :to="{ path: `/chapter_list/${chapterInfo.bookId || routeBookId}` }">目录</router-link>
      <button type="button" @click="changeFont(-1)">A-</button>
      <button type="button" @click="changeFont(1)">A+</button>
      <button type="button" @click="toggleTheme">{{ themeLabel }}</button>
      <button type="button" @click="next">下一章</button>
    </nav>
  </div>
</template>

<script>
import { computed, onMounted, reactive, toRefs, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBookContent, getNextChapterId, getPreChapterId } from '@/api/book'
import AppHeader from '@/components/AppHeader.vue'

export default {
  name: 'ReaderView',
  components: { AppHeader },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const state = reactive({
      data: {},
      fontSize: Number(localStorage.getItem('readerFontSize') || 18),
      theme: localStorage.getItem('readerTheme') || 'paper'
    })

    const chapterInfo = computed(() => state.data.chapterInfo || {})
    const bookInfo = computed(() => state.data.bookInfo || {})
    const chapterName = computed(() => chapterInfo.value.chapterName || '阅读')
    const content = computed(() => state.data.bookContent || '')
    const themeClass = computed(() => {
      if (state.theme === 'green') return 'theme-green'
      if (state.theme === 'night') return 'theme-night'
      return ''
    })
    const themeLabel = computed(() => {
      if (state.theme === 'paper') return '护眼'
      if (state.theme === 'green') return '夜间'
      return '浅色'
    })
    const routeBookId = computed(() => route.params.id)

    const load = async chapterId => {
      const { data } = await getBookContent(chapterId)
      state.data = data || {}
      window.scrollTo(0, 0)
    }

    const previous = async () => {
      const { data } = await getPreChapterId(route.params.chapterId)
      if (!data) {
        ElMessage.warning('已经是第一章了')
        return
      }
      router.push({ path: `/book/${routeBookId.value}/${data}` })
    }

    const next = async () => {
      const { data } = await getNextChapterId(route.params.chapterId)
      if (!data) {
        ElMessage.warning('已经是最后一章了')
        return
      }
      router.push({ path: `/book/${routeBookId.value}/${data}` })
    }

    const changeFont = step => {
      const nextSize = Math.max(16, Math.min(24, state.fontSize + step))
      state.fontSize = nextSize
      localStorage.setItem('readerFontSize', String(nextSize))
    }

    const toggleTheme = () => {
      state.theme = state.theme === 'paper' ? 'green' : state.theme === 'green' ? 'night' : 'paper'
      localStorage.setItem('readerTheme', state.theme)
    }

    watch(() => route.params.chapterId, id => {
      if (id) load(id)
    })

    onMounted(() => load(route.params.chapterId))

    return {
      ...toRefs(state),
      chapterInfo,
      bookInfo,
      chapterName,
      content,
      themeClass,
      themeLabel,
      routeBookId,
      previous,
      next,
      changeFont,
      toggleTheme
    }
  }
}
</script>
