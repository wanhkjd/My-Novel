<template>
  <div class="page no-tab">
    <AppHeader title="书籍详情" back />

    <section class="section page-card">
      <div class="detail-head">
        <img class="book-cover" id="bookCover" :src="cover" :alt="book.bookName || '小说封面'" @error="useFallback" />
        <div>
          <h1 class="detail-title">{{ book.bookName }}</h1>
          <p class="detail-meta">
            <span>作者：{{ book.authorName || '-' }}</span>
            <span>分类：{{ book.categoryName || '-' }}</span>
            <span>状态：{{ book.bookStatus === 0 ? '连载中' : '已完结' }}</span>
            <span>字数：{{ formatWord(book.wordCount) }}</span>
            <span>点击：{{ book.visitCount || 0 }}</span>
          </p>
        </div>
      </div>
      <div class="button-row" style="margin-top: 16px;">
        <button class="primary-button" type="button" @click="readFirst">开始阅读</button>
        <button class="secondary-button" type="button" @click="openChapterList">查看目录</button>
      </div>
    </section>

    <section class="section page-card">
      <div class="section-head">
        <h2 class="section-title">作品简介</h2>
      </div>
      <p class="intro-text" v-html="book.bookDesc || '暂无简介'"></p>
    </section>

    <section v-if="chapterAbout.chapterInfo" class="section page-card">
      <div class="section-head">
        <h2 class="section-title">最新章节</h2>
        <button class="section-action" type="button" @click="openChapterList">全部目录</button>
      </div>
      <a class="chapter-link" href="javascript:void(0)" @click="readChapter(chapterAbout.chapterInfo.id)">
        <span>{{ chapterAbout.chapterInfo.chapterName }}</span>
        <small>{{ chapterAbout.chapterInfo.chapterUpdateTime }}</small>
      </a>
      <p v-if="chapterAbout.contentSummary" class="intro-text" v-html="chapterAbout.contentSummary + '...'"></p>
    </section>

    <section v-if="recBooks.length" class="section">
      <div class="section-head">
        <h2 class="section-title">同类推荐</h2>
      </div>
      <div class="book-list">
        <BookCard v-for="item in recBooks" :key="item.id" :book="item" />
      </div>
    </section>

    <section class="section page-card">
      <div class="section-head">
        <h2 class="section-title">书评</h2>
        <span class="section-action">{{ newestComments.commentTotal || 0 }} 条</span>
      </div>
      <EmptyState v-if="!newestComments.commentTotal" text="还没有书评" />
      <div v-else>
        <article v-for="item in newestComments.comments" :key="item.id" class="comment-item">
          <p class="comment-title">{{ item.commentUser }}</p>
          <p class="comment-content" v-html="item.commentContent"></p>
          <span class="comment-time">{{ item.commentTime }}</span>
        </article>
      </div>
      <div class="form-stack" style="margin-top: 12px;">
        <div class="field">
          <label for="commentContent">发表书评</label>
          <textarea id="commentContent" v-model.trim="commentContent" maxlength="512" placeholder="说说这本书哪里打动了你"></textarea>
        </div>
        <button class="primary-button" type="button" @click="submitComment">发表</button>
      </div>
    </section>
  </div>
</template>

<script>
import { computed, onMounted, reactive, toRefs, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  addVisitCount,
  getBookById,
  getLastChapterAbout,
  listNewestComments,
  listRecBooks
} from '@/api/book'
import { comment } from '@/api/user'
import AppHeader from '@/components/AppHeader.vue'
import BookCard from '@/components/BookCard.vue'
import EmptyState from '@/components/EmptyState.vue'

export default {
  name: 'BookDetail',
  components: { AppHeader, BookCard, EmptyState },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const imgBaseUrl = process.env.VUE_APP_BASE_IMG_URL || ''
    const state = reactive({
      book: {},
      chapterAbout: {},
      recBooks: [],
      newestComments: {},
      commentContent: ''
    })

    const cover = computed(() => state.book.picUrl ? imgBaseUrl + state.book.picUrl : 'default.gif')

    const load = async id => {
      const [bookRes, chapterRes, recRes, commentRes] = await Promise.all([
        getBookById(id),
        getLastChapterAbout({ bookId: id }),
        listRecBooks({ bookId: id }),
        listNewestComments({ bookId: id })
      ])
      state.book = bookRes.data || {}
      state.chapterAbout = chapterRes.data || {}
      state.recBooks = recRes.data || []
      state.newestComments = commentRes.data || {}
      addVisitCount({ bookId: id })
    }

    const readFirst = () => {
      if (!state.book.firstChapterId) {
        ElMessage.warning('暂无可阅读章节')
        return
      }
      router.push({ path: `/book/${state.book.id}/${state.book.firstChapterId}` })
    }
    const readChapter = chapterId => router.push({ path: `/book/${state.book.id}/${chapterId}` })
    const openChapterList = () => router.push({ path: `/chapter_list/${state.book.id}` })
    const useFallback = event => {
      event.target.src = 'default.gif'
    }
    const formatWord = wordCount => {
      const count = Number(wordCount || 0)
      if (count >= 10000) return `${Math.round(count / 10000)}万字`
      return `${count}字`
    }
    const submitComment = async () => {
      if (state.commentContent.length < 10) {
        ElMessage.error('书评不能少于 10 个字')
        return
      }
      await comment({ bookId: state.book.id, commentContent: state.commentContent })
      state.commentContent = ''
      const { data } = await listNewestComments({ bookId: state.book.id })
      state.newestComments = data || {}
      ElMessage.success('发表成功')
    }

    watch(() => route.params.id, id => {
      if (id) load(id)
    })

    onMounted(() => load(route.params.id))

    return {
      ...toRefs(state),
      cover,
      readFirst,
      readChapter,
      openChapterList,
      useFallback,
      formatWord,
      submitComment
    }
  }
}
</script>
