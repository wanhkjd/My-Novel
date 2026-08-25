<template>
  <header class="app-header" :class="{ compact }">
    <button v-if="back" class="icon-button" type="button" aria-label="返回" @click="goBack">
      <span class="chevron-left"></span>
    </button>
    <div class="app-title" :class="{ centered: back && !search }">{{ title }}</div>
    <form v-if="search" class="search-box" @submit.prevent="submit">
      <input v-model.trim="keyword" type="search" enterkeyhint="search" placeholder="搜索书名、作者" aria-label="搜索书名、作者" />
      <button type="submit">搜索</button>
    </form>
    <slot name="right"></slot>
  </header>
</template>

<script>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'

export default {
  name: 'AppHeader',
  props: {
    title: { type: String, default: '小说精品屋' },
    back: { type: Boolean, default: false },
    search: { type: Boolean, default: false },
    modelValue: { type: String, default: '' },
    compact: { type: Boolean, default: false }
  },
  emits: ['update:modelValue', 'search'],
  setup(props, { emit }) {
    const router = useRouter()
    const keyword = ref(props.modelValue)

    watch(() => props.modelValue, value => {
      keyword.value = value
    })

    const submit = () => {
      emit('update:modelValue', keyword.value)
      emit('search', keyword.value)
    }

    const goBack = () => {
      if (window.history.length > 1) {
        router.back()
      } else {
        router.push({ name: 'home' })
      }
    }

    return { keyword, submit, goBack }
  }
}
</script>
