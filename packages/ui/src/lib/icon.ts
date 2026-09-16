import type { ComponentType, SVGProps } from 'react';

/**
 * lucide-react ships a single install shared across this workspace's React 18
 * and React 19 consumers (see pnpm-workspace.yaml's packageExtensions). Its
 * icon components are typed against whichever @types/react that shared
 * install resolves, which doesn't always match the locally-installed
 * @types/react used to check this package's own JSX — cast through this to
 * decouple icon usage from that version skew.
 */
export type IconComponent = ComponentType<SVGProps<SVGSVGElement>>;
