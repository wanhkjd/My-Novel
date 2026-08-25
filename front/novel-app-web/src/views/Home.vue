<template>
  <div class="page">
    <AppHeader title="小说精品屋" search v-model="keyword" @search="goSearch" />

    <section v-if="heroBook" class="section hero-strip" @click="openBook(heroBook)">
      <img class="book-cover" :src="coverOf(heroBook)" :alt="heroBook.bookName" @error="useFallback" />
      <div class="hero-copy">
        <h1>{{ heroBook.bookName }}</h1>
        <p v-if="heroBook.bookDesc" v-html="heroBook.bookDesc"></p>
        <button type="button" class="primary-button">立即阅读</button>
      </div>
    </section>

    <section v-if="topBooks.length" class="section">
      <div class="section-head">
        <h2 class="section-title">今日推荐</h2>
        <router-link class="section-action" :to="{ name: 'discover' }">更多</router-link>
      </div>
      <div class="book-grid">
        <article v-for="book in topBooks.slice(0, 6)" :key="book.bookId || book.id" class="cover-card" @click="openBook(book)">
          <img :src="coverOf(book)" :alt="book.bookName" loading="lazy" @error="useFallback" />
          <h3>{{ book.bookName }}</h3>
          <p>{{ book.authorName }}</p>
        </article>
      </div>
    </section>

    <section v-if="hotRecommend.length" class="section">
      <div class="section-head">
        <h2 class="section-title">热门推荐</h2>
      </div>
      <div class="book-list">
        <BookCard v-for="book in hotRecommend.slice(0, 5)" :key="book.bookId || book.id" :book="book" />
      </div>
    </section>

    <section v-if="goodRecommend.length" class="section">
      <div class="section-head">
        <h2 class="section-title">精品推荐</h2>
      </div>
      <div class="book-list">
        <BookCard v-for="book in goodRecommend.slice(0, 5)" :key="book.bookId || book.id" :book="book" />
      </div>
    </section>

    <section v-if="latestNews.length" class="section">
      <div class="section-head">
        <h2 class="section-title">最新公告</h2>
      </div>
      <div class="menu-list">
        <a v-for="news in latestNews.slice(0, 4)" :key="news.id" href="javascript:void(0)">
          <span>{{ news.title || news.catName || '站内公告' }}</span>
          <span>{{ news.createTime || news.updateTime || '' }}</span>
        </a>
      </div>
    </section>

    <EmptyState v-if="!loading && !heroBook && !hotRecommend.length" text="暂时没有推荐内容" />
    <BottomNav />
  </div>
</template>

<script>
import { computed, onMounted, reactive, toRefs } from 'vue'
import { useRouter } from 'vue-router'
import { listHomeBooks } from '@/api/home'
import { listLatestNews } from '@/api/news'
import AppHeader from '@/components/AppHeader.vue'
import BottomNav from '@/components/BottomNav.vue'
import BookCard from '@/components/BookCard.vue'
import EmptyState from '@/components/EmptyState.vue'

export default {
  name: 'HomeView',
  components: { AppHeader, BottomNav, BookCard, EmptyState },
  setup() {
    const router = useRouter()
    const imgBaseUrl = process.env.VUE_APP_BASE_IMG_URL || ''
    const state = reactive({
      keyword: '',
      loading: true,
      sliderContent: [],
      topBooks: [],
      hotRecommend: [],
      goodRecommend: [],
      latestNews: []
    })

    const heroBook = computed(() => state.sliderContent[0] || state.hotRecommend[0] || state.topBooks[0])

    const loadHome = async () => {
      state.loading = true
      try {
        const { data } = await listHomeBooks()
        state.sliderContent = []
        state.topBooks = []
        state.hotRecommend = []
        state.goodRecommend = []
        ;(data || []).forEach(book => {
          if (book.type === 0) state.sliderContent.push(book)
          if (book.type === 1) state.topBooks.push(book)
          if (book.type === 3) state.hotRecommend.push(book)
          if (book.type === 4) state.goodRecommend.push(book)
        })
      } finally {
        state.loading = false
      }
    }

    const loadNews = async () => {
      try {
        const { data } = await listLatestNews()
        state.latestNews = data || []
      } catch (error) {
        state.latestNews = []
      }
    }

    const coverOf = book => book && book.picUrl ? imgBaseUrl + book.picUrl : 'default.gif'
    const useFallback = event => {
      event.target.src = 'default.gif'
    }
    const openBook = book => {
      const id = book && (book.bookId || book.id)
      if (id) router.push({ path: `/book/${id}` })
    }
    const goSearch = key => {
      router.push({ name: 'discover', query: key ? { key } : {} })
    }

    onMounted(() => {
      loadHome()
      loadNews()
    })

    return {
      ...toRefs(state),
      heroBook,
      coverOf,
      useFallback,
      openBook,
      goSearch
    }
  }
}
</script>
