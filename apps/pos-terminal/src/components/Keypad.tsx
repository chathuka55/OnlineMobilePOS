import { Button } from '@possaas/ui';

const keys = ['7', '8', '9', '4', '5', '6', '1', '2', '3', '0', '.', '⌫'] as const;

export function Keypad({
  onDigit,
  onBackspace,
  onClear,
  onEnter,
}: {
  onDigit: (digit: string) => void;
  onBackspace: () => void;
  onClear: () => void;
  onEnter: () => void;
}) {
  return (
    <div>
      <div className="grid grid-cols-3 gap-2">
        {keys.map((key) => (
          <Button
            key={key}
            type="button"
            variant="outline"
            className="hover:bg-primary/25 h-14 border-white/20 bg-white/10 text-xl font-semibold text-white hover:text-white"
            onClick={() => {
              if (key === '⌫') onBackspace();
              else onDigit(key);
            }}
          >
            {key}
          </Button>
        ))}
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2">
        <Button type="button" variant="secondary" className="h-12" onClick={onClear}>
          Clear
        </Button>
        <Button type="button" className="h-12" onClick={onEnter}>
          Enter
        </Button>
      </div>
    </div>
  );
}
