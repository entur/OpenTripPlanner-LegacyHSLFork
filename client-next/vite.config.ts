import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const VERSION = 'v1';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/debug-client-preview/',
  build: {
    outDir: '../src/client/debug-client-preview',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        assetFileNames: () => {
          return `[name].${VERSION}.css`;
        },
        chunkFileNames: () => {
          return `[name].${VERSION}.js`;
        },
        entryFileNames: () => {
          return `[name].${VERSION}.js`;
        },
      },
    },
  },
});
