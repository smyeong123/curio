/** @type {import('tailwindcss').Config} */
export default {
  // useTheme writes `class="dark"` on <html> when the user picks dark mode.
  // Using the class strategy (not media) lets us honor the persisted preference
  // even when it disagrees with the OS setting.
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#6366F1',
        secondary: '#F59E0B',
      },
    },
  },
  plugins: [],
}
