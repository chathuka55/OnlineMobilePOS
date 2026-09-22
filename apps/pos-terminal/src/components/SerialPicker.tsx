import { useEffect, useState } from 'react';
import type { Item, Serial } from '@possaas/api-client';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  Spinner,
} from '@possaas/ui';
import { api } from '../lib/api';

/**
 * Serialized items (IMEI/serial-tracked) carry their own per-unit warranty end
 * date, so checkout must know exactly which physical unit was sold - the
 * backend rejects a serial-tracked line that doesn't supply one serial id per
 * unit sold (SERIAL_COUNT_MISMATCH).
 */
export function SerialPicker({
  item,
  excludeIds,
  onPick,
  onClose,
}: {
  item: Item | null;
  excludeIds: string[];
  onPick: (serial: Serial) => void;
  onClose: () => void;
}) {
  const [serials, setSerials] = useState<Serial[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!item) return;
    setLoading(true);
    api.serials
      .list({ itemId: item.id, status: 'IN_STOCK', size: 100 })
      .then((data) => setSerials(Array.isArray(data) ? data : data.content))
      .catch(() => setSerials([]))
      .finally(() => setLoading(false));
  }, [item]);

  const available = serials.filter((s) => !excludeIds.includes(s.id));

  return (
    <Dialog open={!!item} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Select serial — {item?.name}</DialogTitle>
          <DialogDescription>
            This item tracks warranty per unit. Pick the exact serial/IMEI being sold.
          </DialogDescription>
        </DialogHeader>
        {loading ? (
          <div className="flex justify-center py-8">
            <Spinner size="lg" />
          </div>
        ) : available.length === 0 ? (
          <p className="text-muted-foreground py-6 text-center text-sm">
            No available serials in stock for this item.
          </p>
        ) : (
          <ul className="max-h-72 space-y-1 overflow-y-auto">
            {available.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  className="flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm hover:bg-slate-50"
                  onClick={() => onPick(s)}
                >
                  <span className="font-mono">{s.serialNumber}</span>
                  {s.warrantyEndsOn && (
                    <span className="text-muted-foreground text-xs">
                      Warranty until {new Date(s.warrantyEndsOn).toLocaleDateString()}
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
        <Button variant="outline" onClick={onClose}>
          Cancel
        </Button>
      </DialogContent>
    </Dialog>
  );
}
