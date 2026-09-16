import * as React from 'react';
import { cn } from '../lib/cn';

export interface SpinnerProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: 'sm' | 'md' | 'lg';
}

export function Spinner({ className, size = 'md', ...props }: SpinnerProps) {
  const sizeClass =
    size === 'sm' ? 'h-4 w-4 border-2' : size === 'lg' ? 'h-10 w-10 border-4' : 'h-6 w-6 border-2';
  return (
    <div
      role="status"
      aria-label="Loading"
      className={cn(
        'border-primary inline-block animate-spin rounded-full border-t-transparent',
        sizeClass,
        className,
      )}
      {...props}
    />
  );
}
