<template>
  <div class="page">
    <AppHeader title="排行榜" />

    <section class="section">
      <div class="chip-row">
        <button class="chip" :class="{ active: rankType === 'visit' }" type="button" @click="loadVisit">点击榜</button>
        <button class="chip" :class="{ active: rankType === 'newest' }" type="button" @click="loadNewest">新书榜</button>
        <button class="chip" :class="{ active: rankType === 'update' }" type="button" @click="loadUpdate">更新榜</button>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <h2 class="section-title">{{ rankName }}</h2>
      </div>
      <div class="book-list">
        <article v-for="(book, index) in books" :key="book.id" class="list-row" @click="openBook(book.id)">
          <span class="rank-number">{{ index + 1 }}</span>
          <div class="row-main">
            <strong>{{ book.bookName }}</strong>
            <span>{{ book.categoryName }} · {{ book.authorName }} · {{ formatWord(book.wordCount) }}</span>
          </div>
          <span class="section-action">查看</span>
        </article>
      </div>
      <EmptyState v-if="!loading && books.length === 0" text="榜单暂无数据" />
    </section>

    <BottomNav />
  </div>
</template>

<script>
import { onMounted, reactive, toRefs } from 'vue'
import { useRouter } from 'vue-router'
import { listNewestRankBooks, listUpdateRankBooks, listVisitRankBooks } from '@/api/book'
import AppHeader from '@/components/AppHeader.vue'
import BottomNav from '@/components/BottomNav.vue'
import EmptyState from '@/components/EmptyState.vue'

export default {
  name: 'RankView',
  components: { AppHeader, BottomNav, EmptyState },
  setup() {
    const router = useRouter()
    const state = reactive({
      rankType: 'visit',
      rankName: '点击榜',
      books: [],
      loading: false
    })

    const load = async (type, name, loader) => {
      state.rankType = type
      state.rankName = name
      state.loading = true
      try {
        const { data } = await loader()
        state.books = data || []
      } finally {
        state.loading = false
      }
    }

    const loadVisit = () => load('visit', '点击榜', listVisitRankBooks)
    const loadNewest = () => load('newest', '新书榜', listNewestRankBooks)
    const loadUpdate = () => load('update', '更新榜', listUpdateRankBooks)
    const openBook = id => router.push({ path: `/book/${id}` })
    const formatWord = wordCount => {
      const count = Number(wordCount || 0)
      if (count >= 10000) return `${Math.round(count / 10000)}万字`
      return `${count}字`
    }

    onMounted(loadVisit)

    return { ...toRefs(state), loadVisit, loadNewest, loadUpdate, openBook, formatWord }
  }
}
</script>
