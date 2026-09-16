import config from '@possaas/eslint-config/react';

export default [
  ...config,
  {
    ignores: ['src-tauri/**'],
  },
];
