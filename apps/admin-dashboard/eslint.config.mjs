import config from '@possaas/eslint-config/next';

export default [
  ...config,
  {
    ignores: ['.next/**'],
  },
];
