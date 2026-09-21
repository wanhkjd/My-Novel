<template>
  <transition name="fade">
    <div v-show="show" class="back-top" @click="toTop">
      <el-icon :size="20"><Top /></el-icon>
    </div>
  </transition>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
const show = ref(false)
const onScroll = () => { show.value = window.scrollY > 300 }
const toTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })
onMounted(() => window.addEventListener('scroll', onScroll))
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<style scoped>
.back-top { position: fixed; right: 32px; bottom: 96px; width: 44px; height: 44px; border-radius: 50%; background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow-md); display: flex; align-items: center; justify-content: center; cursor: pointer; color: var(--brand); z-index: 50; }
</style>
