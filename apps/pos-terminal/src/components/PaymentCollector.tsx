import { useState } from 'react';
import type { PaymentMethod } from '@possaas/api-client';
import { Button, Input } from '@possaas/ui';

export type CollectedPayment = {
  method: PaymentMethod;
  amount: number;
  reference?: string;
  bankName?: string;
  chequeNumber?: string;
  chequeDate?: string;
};

const METHODS: PaymentMethod[] = ['CASH', 'CARD', 'BANK_TRANSFER', 'CHEQUE'];

/** Amount + method entry for collecting a payment against a balance — full or partial. */
export function PaymentCollector({
  balanceDue,
  submitting,
  submitLabel,
  onSubmit,
}: {
  balanceDue: number;
  submitting?: boolean;
  submitLabel: string;
  onSubmit: (payment: CollectedPayment) => void;
}) {
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [amount, setAmount] = useState(balanceDue);
  const [reference, setReference] = useState('');
  const [bankName, setBankName] = useState('');
  const [chequeNumber, setChequeNumber] = useState('');
  const [chequeDate, setChequeDate] = useState('');

  const valid = amount > 0 && amount <= balanceDue + 0.001
    && (method !== 'CHEQUE' || (chequeNumber.trim() && chequeDate));

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-4 gap-2">
        {METHODS.map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setMethod(m)}
            className={`rounded-lg border px-2 py-2 text-xs font-semibold transition ${
              method === m
                ? 'border-primary bg-primary/15 text-navy'
                : 'border-border bg-white text-muted-foreground hover:bg-slate-50'
            }`}
          >
            {m.replace('_', ' ')}
          </button>
        ))}
      </div>

      <div>
        <label className="text-muted-foreground text-xs font-semibold uppercase">Amount</label>
        <Input
          type="number"
          min="0.01"
          max={balanceDue}
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(Math.min(Number(e.target.value) || 0, balanceDue))}
          className="mt-1"
        />
        <button
          type="button"
          className="text-primary mt-1 text-xs underline"
          onClick={() => setAmount(balanceDue)}
        >
          Pay full balance ({balanceDue.toFixed(2)})
        </button>
      </div>

      {method === 'CHEQUE' ? (
        <div className="grid grid-cols-2 gap-2">
          <Input
            placeholder="Cheque No"
            value={chequeNumber}
            onChange={(e) => setChequeNumber(e.target.value)}
          />
          <Input
            placeholder="Bank"
            value={bankName}
            onChange={(e) => setBankName(e.target.value)}
          />
          <Input
            type="date"
            value={chequeDate}
            onChange={(e) => setChequeDate(e.target.value)}
            className="col-span-2"
          />
        </div>
      ) : (
        <Input
          placeholder="Reference (optional)"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
        />
      )}

      <Button
        size="lg"
        className="w-full"
        disabled={!valid || submitting}
        onClick={() =>
          onSubmit({
            method,
            amount,
            reference: reference || undefined,
            bankName: bankName || undefined,
            chequeNumber: chequeNumber || undefined,
            chequeDate: chequeDate || undefined,
          })
        }
      >
        {submitting ? 'Processing…' : submitLabel}
      </Button>
    </div>
  );
}
