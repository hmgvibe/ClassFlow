import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [vue()],
  define: {
    'process.env.NODE_ENV': JSON.stringify('production'),
  },
  build: {
    outDir: '.',
    emptyOutDir: false,
    cssCodeSplit: false,
    lib: {
      entry: resolve(import.meta.dirname, 'src/main.ts'),
      name: 'ClassFlow',
      formats: ['iife'],
      fileName: () => 'js/classflow-main.js',
    },
    rollupOptions: {
      output: {
        assetFileNames: asset => asset.name?.endsWith('.css') ? 'css/classflow-main.css' : 'assets/[name][extname]',
      },
    },
  },
})

