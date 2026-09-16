import * as React from 'react';
import { Inbox } from 'lucide-react';
import { cn } from '../lib/cn';
import type { IconComponent } from '../lib/icon';

const DefaultIcon = Inbox as IconComponent;

export interface EmptyStateProps extends React.HTMLAttributes<HTMLDivElement> {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
}

export function EmptyState({
  title,
  description,
  icon,
  action,
  className,
  ...props
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'border-border bg-card/60 flex flex-col items-center justify-center rounded-xl border border-dashed px-6 py-12 text-center',
        className,
      )}
      {...props}
    >
      <div className="bg-accent text-primary mb-4 flex h-12 w-12 items-center justify-center rounded-full">
        {icon ?? <DefaultIcon className="h-6 w-6" />}
      </div>
      <h3 className="text-navy text-base font-semibold">{title}</h3>
      {description ? (
        <p className="text-muted-foreground mt-1 max-w-sm text-sm">{description}</p>
      ) : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
