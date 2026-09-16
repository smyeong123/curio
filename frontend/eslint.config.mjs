import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'

/**
 * Deliberately narrow ESLint setup focused on correctness bugs that the type
 * checker cannot catch. The motivating incident: a computed() callback
 * referenced a const declared later in the file. TypeScript accepts that
 * (the closure runs "later"), but Vue evaluates computeds during setup when a
 * watcher reads them — so it crashed at runtime with a temporal-dead-zone
 * ReferenceError, but only when the store was already populated (back-nav).
 * `no-use-before-define` flags that ordering statically.
 *
 * Style/formatting rules are intentionally left out.
 */
export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**'] },

  // Base .vue parsing (no stylistic rule sets) + TS in <script setup lang="ts">
  ...pluginVue.configs['flat/base'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
        sourceType: 'module',
      },
    },
  },
  // Plain TypeScript files use the TS parser directly
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: { sourceType: 'module' },
    },
  },
  {
    files: ['src/**/*.{ts,vue}', 'tests/**/*.ts'],
    plugins: { '@typescript-eslint': tseslint.plugin },
    rules: {
      'no-use-before-define': 'off',
      '@typescript-eslint/no-use-before-define': [
        'error',
        {
          functions: false, // function declarations hoist — safe
          classes: true,
          variables: true, // the TDZ trap: const arrow fns used before declaration
          ignoreTypeReferences: true, // types/interfaces are erased — no runtime risk
        },
      ],
    },
  }
)
