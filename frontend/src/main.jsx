/**
 * main.jsx — Application entry point
 *
 * Bootstraps the React app with three top-level providers:
 *  - React.StrictMode   – surfaces unsafe lifecycle patterns in development
 *  - BrowserRouter      – enables client-side routing via react-router-dom
 *  - AppProvider        – global state (ASHA worker session, patients, assessments)
 *
 * i18n.js is imported for its side-effect: initialises i18next before any
 * component renders so translated strings are available immediately.
 * index.css brings in Tailwind's base/components/utilities layers.
 */
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AppProvider } from './context/AppContext.jsx'
import './i18n.js'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <AppProvider>
        <App />
      </AppProvider>
    </BrowserRouter>
  </React.StrictMode>
)
