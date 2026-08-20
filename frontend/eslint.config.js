import eslint from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import typescript from 'typescript-eslint'

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
