<script setup>
</script>

<template>
  <div id="app">
    <header class="app-header">
      <h1>个人知识库</h1>
    </header>
    <main class="app-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
#app {
  min-height: 100vh;
  background: #f5f5f5;
}
.app-header {
  background: #1976d2;
  color: white;
  padding: 16px 24px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.app-header h1 {
  margin: 0;
  font-size: 22px;
}
.app-content {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px;
}
</style>