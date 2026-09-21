<template>
  <footer class="footer">
    <div class="container">
      <p class="brand"><span class="logo-mark">墨</span> 墨书 · 在线书城</p>
      <p class="sub">基于 Spring Boot 3 + Vue 3 的小说阅读平台演示 · 内含 AI 智能书童</p>
      <p class="copy">© 2026 墨书 Demo. 仅供学习交流。</p>
    </div>
  </footer>
</template>

<script setup></script>

<style scoped>
.footer { background: #2b241f; color: #cbbfae; margin-top: var(--sp-6); padding: var(--sp-6) 0; text-align: center; }
.footer .brand { font-family: var(--font-serif); font-size: 18px; color: #fff; }
.footer .logo-mark { color: var(--brand); }
.footer .sub { margin: 8px 0; font-size: 13px; }
.footer .copy { font-size: 12px; color: #8a7f70; }
</style>
