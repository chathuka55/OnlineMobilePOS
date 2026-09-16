import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@possaas/ui';
import { PageHeader } from '@/components/page-header';

export function SectionPlaceholder({
  title,
  description,
  bullets,
}: {
  title: string;
  description: string;
  bullets: string[];
}) {
  return (
    <div>
      <PageHeader title={title} description={description} />
      <Card className="animate-fade-up">
        <CardHeader>
          <CardTitle>Module workspace</CardTitle>
          <CardDescription>
            Functional shell ready for domain APIs — workflows below match Easy POS sections.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="grid gap-3 sm:grid-cols-2">
            {bullets.map((b) => (
              <li
                key={b}
                className="border-border/70 bg-background/70 text-foreground rounded-lg border px-4 py-3 text-sm"
              >
                {b}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
