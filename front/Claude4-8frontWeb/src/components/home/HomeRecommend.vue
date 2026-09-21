<template>
  <div class="home-recommend">
    <div v-if="carousel.length" class="carousel-row">
      <el-carousel height="260px" :interval="4000">
        <el-carousel-item v-for="b in carousel" :key="b.bookId" @click="go(b.bookId)">
          <div class="slide">
            <img :src="cover(b.picUrl)" @error="onErr" />
            <div class="slide-info">
              <h3>{{ b.bookName }}</h3>
              <p>{{ b.authorName }}</p>
              <p class="text-clamp-2">{{ b.bookDesc }}</p>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </div>
    <div v-for="grp in groups" :key="grp.type" class="rec-group">
      <h3 class="section-title">{{ grp.label }}</h3>
      <div class="rec-grid">
        <BookCard v-for="b in grp.items" :key="b.bookId" :book="toBook(b)" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import BookCard from '@/components/common/BookCard.vue'

const props = defineProps({ books: { type: Array, default: () => [] } })
const router = useRouter()
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const FALLBACK = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="260"><rect width="200" height="260" fill="%23eae4db"/></svg>'
const cover = (u) => u ? IMG + u : FALLBACK
const onErr = (e) => { e.target.src = FALLBACK }
const carousel = computed(() => props.books.filter(b => b.type === 0))
const LABELS = { 2: '本周强推', 3: '热门推荐', 4: '精品推荐' }
const groups = computed(() =>
  [2, 3, 4].map(t => ({ type: t, label: LABELS[t], items: props.books.filter(b => b.type === t) }))
           .filter(g => g.items.length))
const toBook = (b) => ({ id: b.bookId, picUrl: b.picUrl, bookName: b.bookName, authorName: b.authorName, bookDesc: b.bookDesc })
const go = (id) => router.push(`/book/${id}`)
</script>

<style scoped>
.slide { position: relative; height: 260px; cursor: pointer; }
.slide img { width: 100%; height: 100%; object-fit: cover; filter: brightness(0.6); }
.slide-info { position: absolute; left: 32px; bottom: 32px; color: #fff; max-width: 60%; }
.slide-info h3 { font-size: 26px; color: #fff; }
.rec-group { margin-top: var(--sp-6); }
.rec-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--sp-4); }
@media (max-width: 900px) { .rec-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
