import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  base: './',
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // Inline registration so no extra script tag is needed
      injectRegister: 'auto',
      workbox: {
        // Cache all static assets produced by the build
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}'],
        // Serve the app shell for any navigation request (SPA fallback)
        navigateFallback: 'index.html',
        // Don't intercept API calls — those should always hit the network
        navigateFallbackDenylist: [/^\/api\//],
        // Keep the service worker up to date silently
        skipWaiting: true,
        clientsClaim: true,
      },
      manifest: {
        name: 'Sakhi — AI Clinical Companion',
        short_name: 'Sakhi',
        description: 'AI clinical decision support for ASHA workers in rural India',
        theme_color: '#2563EB',
        background_color: '#ffffff',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        // Icons intentionally omitted — add pwa-192x192.png / pwa-512x512.png
        // to frontend/public/ to enable home-screen installation
      },
    }),
  ],
  server: {
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
    },
  },
})
