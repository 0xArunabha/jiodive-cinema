import { defineConfig } from 'vite';
import basicSsl from '@vitejs/plugin-basic-ssl';
import os from 'node:os';
import path from 'node:path';

export default defineConfig({
  base: './',
  // Store Vite cache in system temp folder outside OneDrive to prevent EPERM file locking
  cacheDir: path.join(os.tmpdir(), 'vite-cache-jiodive'),
  plugins: [
    basicSsl()
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          three: ['three']
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    https: true
  }
});
