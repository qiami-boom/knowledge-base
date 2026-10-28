<script setup>
</script>

<template>
  <div id="app">
    <header class="app-header">
      <h1>个人知识库</h1>
      <nav class="nav">
        <router-link to="/">知识列表</router-link>
        <router-link to="/review">今日复习</router-link>
      </nav>
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #1976d2;
  color: white;
  padding: 16px 24px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.app-header h1 {
  margin: 0;
  font-size: 22px;
}
.nav a {
  color: #fff;
  text-decoration: none;
  margin-left: 20px;
  opacity: 0.75;
  font-size: 15px;
}
.nav a.router-link-active {
  opacity: 1;
  font-weight: 600;
  border-bottom: 2px solid #fff;
  padding-bottom: 2px;
}
.app-content {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px;
}
</style>