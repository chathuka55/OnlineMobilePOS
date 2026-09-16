import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  transpilePackages: ['@possaas/ui', '@possaas/api-client'],
  experimental: {
    optimizePackageImports: ['lucide-react', '@possaas/ui'],
  },
};

export default nextConfig;
