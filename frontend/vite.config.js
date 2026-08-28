import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server:{
    port: 5173,
    proxy:{
      //将/api开头的请求自动转发给后端8081
      '/api':{
        target:'http://localhost:8081',
        changeOrigin:true
      }
    }
  }
})
