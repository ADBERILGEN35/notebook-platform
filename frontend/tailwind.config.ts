import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#1f108e',
          container: '#3730a3',
          fixed: '#e2dfff',
          'fixed-dim': '#c3c0ff',
          50: '#eef2ff',
          100: '#e0e7ff',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
        },
        surface: {
          DEFAULT: '#fcf8ff',
          container: {
            lowest: '#ffffff',
            low: '#f6f2fc',
            DEFAULT: '#f0ecf6',
          },
        },
        'on-surface': '#1b1b22',
        'on-surface-variant': '#464553',
        'outline-variant': '#c8c4d5',
        outline: '#777584',
        error: {
          DEFAULT: '#ba1a1a',
          container: '#ffdad6',
        },
        secondary: {
          DEFAULT: '#515f74',
          container: '#d5e3fc',
        },
      },
      fontFamily: {
        display: ['Manrope', 'Inter', 'system-ui', 'sans-serif'],
        body: ['Inter', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        'headline-md': ['24px', { lineHeight: '1.3', fontWeight: '600' }],
        'headline-sm': ['18px', { lineHeight: '1.4', fontWeight: '600' }],
        'body-md': ['14px', { lineHeight: '1.5', fontWeight: '400' }],
        'label-md': ['12px', { lineHeight: '1', fontWeight: '500' }],
      },
      borderRadius: {
        xl: '0.75rem',
      },
      boxShadow: {
        card: '0 1px 2px rgba(15, 23, 42, 0.08)',
        'auth-card': '0px 10px 15px -3px rgba(15, 23, 42, 0.05)',
      },
      spacing: {
        gutter: '24px',
      },
    },
  },
  darkMode: 'media',
  plugins: [],
} satisfies Config

