<template>
  <article class="book-card" @click="openBook">
    <img class="book-cover" :src="cover" :alt="book.bookName || '小说封面'" loading="lazy" @error="useFallback" />
    <div class="book-card-body">
      <h3 v-html="book.bookName"></h3>
      <p class="book-meta">
        <span v-if="book.authorName">{{ book.authorName }}</span>
        <span v-if="book.categoryName">{{ book.categoryName }}</span>
      </p>
      <p v-if="book.bookDesc" class="book-desc" v-html="book.bookDesc"></p>
      <p v-if="book.lastChapterName" class="book-latest">{{ book.lastChapterName }}</p>
    </div>
  </article>
</template>

<script>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

export default {
  name: 'BookCard',
  props: {
    book: { type: Object, required: true }
  },
  setup(props) {
    const router = useRouter()
    const imgBaseUrl = process.env.VUE_APP_BASE_IMG_URL || ''
    const cover = computed(() => props.book.picUrl ? imgBaseUrl + props.book.picUrl : 'default.gif')

    const openBook = () => {
      const id = props.book.id || props.book.bookId
      if (id) {
        router.push({ path: `/book/${id}` })
      }
    }

    const useFallback = event => {
      event.target.src = 'default.gif'
    }

    return { cover, openBook, useFallback }
  }
}
</script>
