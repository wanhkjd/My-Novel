<template>
  <div class="page no-tab">
    <AppHeader title="目录" back />

    <section class="section page-card">
      <h1 class="detail-title">{{ book.bookName }}</h1>
      <p class="detail-meta">
        <span>作者：{{ book.authorName || '-' }}</span>
        <span>共 {{ chapters.length }} 章</span>
      </p>
    </section>

    <section class="section">
      <div class="chapter-grid">
        <a v-for="chapter in chapters" :key="chapter.id" class="chapter-link" href="javascript:void(0)" @click="readChapter(chapter.id)">
          <span>{{ chapter.chapterName }}</span>
          <small>{{ chapter.isVip === 1 ? '收费' : '免费' }}</small>
        </a>
      </div>
      <EmptyState v-if="!loading && chapters.length === 0" text="暂无章节" />
    </section>
  </div>
</template>

<script>
import { onMounted, reactive, toRefs } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBookById, listChapters } from '@/api/book'
import AppHeader from '@/components/AppHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

export default {
  name: 'ChapterList',
  components: { AppHeader, EmptyState },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const state = reactive({
      book: {},
      chapters: [],
      loading: true
    })

    const load = async () => {
      state.loading = true
      try {
        const [bookRes, chapterRes] = await Promise.all([
          getBookById(route.params.bookId),
          listChapters({ bookId: route.params.bookId })
        ])
        state.book = bookRes.data || {}
        state.chapters = chapterRes.data || []
      } finally {
        state.loading = false
      }
    }

    const readChapter = chapterId => {
      router.push({ path: `/book/${route.params.bookId}/${chapterId}` })
    }

    onMounted(load)

    return { ...toRefs(state), readChapter }
  }
}
</script>
