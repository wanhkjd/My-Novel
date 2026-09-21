<template>
  <div class="portal">
    <AppNavbar />
    <main class="portal-main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in"><component :is="Component" /></transition>
      </router-view>
    </main>
    <AppFooter />
    <BackTop />
  </div>
</template>

<script setup>
import AppNavbar from '@/components/common/AppNavbar.vue'
import AppFooter from '@/components/common/AppFooter.vue'
import BackTop from '@/components/common/BackTop.vue'
</script>

<style scoped>
.portal { min-height: 100vh; display: flex; flex-direction: column; }
.portal-main { flex: 1; }
</style>
