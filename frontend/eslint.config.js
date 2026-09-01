import eslint from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import typescript from 'typescript-eslint'

const browserGlobals = Object.fromEntries(
  [
    'AbortController',
    'AbortSignal',
    'clearTimeout',
    'document',
    'Element',
    'Event',
    'HTMLElement',
    'HTMLImageElement',
    'HTMLInputElement',
    'HTMLTableElement',
    'HTMLTextAreaElement',
    'Node',
    'PointerEvent',
    'requestAnimationFrame',
    'setTimeout',
    'URL',
    'window',
  ].map((name) => [name, 'readonly']),
)

export default typescript.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', '*.d.ts'],
  },
  eslint.configs.recommended,
  ...typescript.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      globals: browserGlobals,
      parserOptions: {
        parser: typescript.parser,
      },
    },
  },
  {
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
  prettier,
)
