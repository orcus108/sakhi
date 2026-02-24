/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['DM Sans', 'sans-serif'],
      },
      colors: {
        brand: {
          green: '#16a34a',   // green-600
          light: '#dcfce7',   // green-100
        },
      },
      maxWidth: {
        mobile: '430px',
      },
    },
  },
  plugins: [],
}
