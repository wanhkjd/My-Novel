<template>
  <div class="page">
    <AppHeader title="分类" search v-model="keyword" @search="searchByKeyword" />

    <section class="section">
      <div class="chip-row" aria-label="频道">
        <button class="chip" :class="{ active: workDirection === 0 }" type="button" @click="loadCategories(0)">男频</button>
        <button class="chip" :class="{ active: workDirection === 1 }" type="button" @click="loadCategories(1)">女频</button>
      </div>
      <div class="chip-row" aria-label="分类">
        <button class="chip" :class="{ active: categoryId === null }" type="button" @click="changeCategory(null)">不限</button>
        <button v-for="item in categories" :key="item.id" class="chip" :class="{ active: categoryId === item.id }" type="button" @click="changeCategory(item.id)">
          {{ item.name }}
        </button>
      </div>
      <div class="chip-row" aria-label="状态">
        <button class="chip" :class="{ active: bookStatus === null }" type="button" @click="changeStatus(null)">不限</button>
        <button class="chip" :class="{ active: bookStatus === 0 }" type="button" @click="changeStatus(0)">连载中</button>
        <button class="chip" :class="{ active: bookStatus === 1 }" type="button" @click="changeStatus(1)">已完结</button>
      </div>
      <div class="chip-row" aria-label="排序">
        <button class="chip" :class="{ active: sort === null }" type="button" @click="changeSort(null)">综合</button>
        <button class="chip" :class="{ active: sort === 'last_chapter_update_time' }" type="button" @click="changeSort('last_chapter_update_time')">最新</button>
        <button class="chip" :class="{ active: sort === 'visit_count' }" type="button" @click="changeSort('visit_count')">点击</button>
        <button class="chip" :class="{ active: sort === 'word_count' }" type="button" @click="changeSort('word_count')">字数</button>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <h2 class="section-title">{{ keyword ? '搜索结果' : '全部作品' }}</h2>
        <span class="section-action">共 {{ total }} 本</span>
      </div>
      <div class="book-list">
        <BookCard v-for="book in books" :key="book.id" :book="book" />
      </div>
      <EmptyState v-if="!loading && books.length === 0" text="没有找到符合条件的小说" />
      <button v-if="books.length < total" class="secondary-button load-more" type="button" @click="loadMore">加载更多</button>
    </section>

    <BottomNav />
  </div>
</template>

<script>
import { onMounted, reactive, toRefs, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listCategorys, searchBooks } from '@/api/book'
import AppHeader from '@/components/AppHeader.vue'
import BottomNav from '@/components/BottomNav.vue'
import BookCard from '@/components/BookCard.vue'
import EmptyState from '@/components/EmptyState.vue'

export default {
  name: 'DiscoverView',
  components: { AppHeader, BottomNav, BookCard, EmptyState },
  setup() {
    const route = useRoute()
    const state = reactive({
      keyword: '',
      workDirection: 0,
      categoryId: null,
      bookStatus: null,
      sort: null,
      categories: [],
      books: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      loading: false
    })

    const buildParams = pageNum => ({
      keyword: state.keyword || null,
      workDirection: state.workDirection,
      categoryId: state.categoryId,
      bookStatus: state.bookStatus,
      sort: state.sort,
      pageNum,
      pageSize: state.pageSize
    })

    const search = async (append = false) => {
      state.loading = true
      try {
        const targetPage = append ? state.pageNum + 1 : 1
        const { data } = await searchBooks(buildParams(targetPage))
        state.pageNum = data.pageNum || targetPage
        state.total = Number(data.total || 0)
        state.books = append ? state.books.concat(data.list || []) : (data.list || [])
      } finally {
        state.loading = false
      }
    }

    const loadCategories = async workDirection => {
      state.workDirection = workDirection
      state.categoryId = null
      const { data } = await listCategorys({ workDirection })
      state.categories = data || []
      search()
    }

    const changeCategory = id => {
      state.categoryId = id
      search()
    }
    const changeStatus = value => {
      state.bookStatus = value
      search()
    }
    const changeSort = value => {
      state.sort = value
      search()
    }
    const searchByKeyword = key => {
      state.keyword = key
      search()
    }
    const loadMore = () => search(true)

    watch(() => route.query.key, value => {
      state.keyword = value || ''
      search()
    })

    onMounted(() => {
      state.keyword = route.query.key || ''
      loadCategories(0)
    })

    return {
      ...toRefs(state),
      loadCategories,
      changeCategory,
      changeStatus,
      changeSort,
      searchByKeyword,
      loadMore
    }
  }
}
</script>
