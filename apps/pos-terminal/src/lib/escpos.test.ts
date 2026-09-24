/**
 * Unit tests for the ESC/POS encoder.
 *
 * These need vitest, which is not installed yet: the machine this was written on
 * had no route to the npm registry, and adding the dependency without being able
 * to update pnpm-lock.yaml would have broken CI's --frozen-lockfile install. To
 * turn them on:
 *
 *   pnpm --filter @possaas/pos-terminal add -D vitest
 *   # add "test": "vitest run" to apps/pos-terminal/package.json
 *   # drop the "exclude" line from apps/pos-terminal/tsconfig.json
 *
 * Every assertion below was run and passed before being committed, against this
 * exact file, using a stand-in for vitest's describe/it/expect. Two deliberate
 * breakages of escpos.ts (truncating the figure instead of the label, and
 * emitting raw code points instead of replacing unprintable ones) were each
 * caught, so these are not assertions that pass no matter what.
 */
import { describe, expect, it } from 'vitest';
import {
  amount,
  columnsFor,
  encodeReceipt,
  layoutReceipt,
  quantity,
  renderReceiptText,
  toBytes,
  twoColumn,
  wrap,
  type ReceiptModel,
} from './escpos';

const bill: ReceiptModel = {
  businessName: 'Sunrise Mobile',
  addressLines: ['No 42, Galle Road'],
  phone: '011 2345678',
  vatTin: '114233445-7000',
  billNumber: 'INV-000123',
  billedAt: '2026-09-24T10:30:00',
  cashierName: 'Nimal',
  customerName: 'Kasun Perera',
  currency: 'LKR',
  lines: [
    { name: 'Redmi Note 13', quantity: 1, unitPrice: '64500.00', amount: '64500.00' },
    { name: 'Tempered Glass', quantity: 2, unitPrice: '750.00', amount: '1500.00' },
  ],
  subtotal: '66000.00',
  discountTotal: '1000.00',
  taxTotal: '11700.00',
  grandTotal: '76700.00',
  balanceDue: '0.00',
  payments: [{ method: 'CASH', amount: '80000.00' }],
  changeAmount: '3300.00',
  footer: 'Thank you! Warranty claims need this receipt.',
};

const printed = (width: 58 | 80) => renderReceiptText(layoutReceipt(bill, width), width);

describe('paper widths', () => {
  it('uses 32 columns on 58mm and 48 on 80mm', () => {
    expect(columnsFor(58)).toBe(32);
    expect(columnsFor(80)).toBe(48);
  });

  it('never lets a line overflow the roll', () => {
    for (const width of [58, 80] as const) {
      for (const line of printed(width).split('\n')) {
        expect(line.length).toBeLessThanOrEqual(columnsFor(width));
      }
    }
  });
});

describe('money and quantity formatting', () => {
  it('groups thousands and always shows two decimals', () => {
    expect(amount('64500')).toBe('64,500.00');
    expect(amount(1234567.5)).toBe('1,234,567.50');
    expect(amount('0')).toBe('0.00');
  });

  it('keeps the minus sign outside the grouping', () => {
    expect(amount(-1500)).toBe('-1,500.00');
  });

  it('survives a null or unparseable amount rather than printing NaN', () => {
    expect(amount(null)).toBe('0.00');
    expect(amount('not a number')).toBe('0.00');
  });

  it('prints whole quantities without decimals', () => {
    expect(quantity(2)).toBe('2');
    expect(quantity('2.000')).toBe('2');
    expect(quantity(1.5)).toBe('1.5');
  });
});

describe('twoColumn', () => {
  it('right-aligns the figure against the line width', () => {
    expect(twoColumn('Subtotal', '66,000.00', 32)).toBe('Subtotal               66,000.00');
    expect(twoColumn('Subtotal', '66,000.00', 32)).toHaveLength(32);
  });

  /** A truncated total is a wrong receipt, so the label is what gives way. */
  it('truncates the label, never the figure', () => {
    const line = twoColumn('An extremely long label indeed', '1,234,567.89', 24);
    expect(line).toHaveLength(24);
    expect(line.endsWith('1,234,567.89')).toBe(true);
  });
});

describe('wrap', () => {
  it('breaks on spaces without exceeding the width', () => {
    expect(wrap('Samsung Galaxy A55 Dual SIM', 16)).toEqual(['Samsung Galaxy', 'A55 Dual SIM']);
  });

  it('hard-splits a word longer than the line instead of dropping it', () => {
    expect(wrap('AAAAAAAAAAAAAAAAAAAA', 8)).toEqual(['AAAAAAAA', 'AAAAAAAA', 'AAAA']);
  });
});

describe('receipt layout', () => {
  it('puts every figure on the right edge of a 58mm roll', () => {
    const text = printed(58);
    expect(text).toContain('Subtotal               66,000.00');
    expect(text).toContain('Discount               -1,000.00');
    expect(text).toContain('Tax                    11,700.00');
    expect(text).toContain('TOTAL LKR              76,700.00');
    expect(text).toContain('Change                  3,300.00');
  });

  it('prints each line as a name then a qty x price row', () => {
    expect(printed(58)).toContain('Tempered Glass\n  2 x 750.00            1,500.00');
  });

  it('shows the VAT registration number when the shop has one', () => {
    expect(printed(58)).toContain('VAT Reg: 114233445-7000');
  });

  /**
   * Step 4 made VAT conditional on registration; an unregistered shop must not
   * print a TIN or a tax line it did not charge.
   */
  it('omits the VAT line and the tax row for an unregistered shop', () => {
    const unregistered: ReceiptModel = { ...bill, vatTin: null, taxTotal: '0.00' };
    const text = renderReceiptText(layoutReceipt(unregistered, 58), 58);
    expect(text).not.toContain('VAT Reg');
    expect(text).not.toContain('Tax ');
  });

  it('omits discount, change and balance rows when they are zero', () => {
    const plain: ReceiptModel = {
      ...bill,
      discountTotal: '0',
      changeAmount: '0',
      balanceDue: '0',
    };
    const text = renderReceiptText(layoutReceipt(plain, 58), 58);
    expect(text).not.toContain('Discount');
    expect(text).not.toContain('Change');
    expect(text).not.toContain('Balance Due');
  });

  it('shows a balance due when the bill is not settled', () => {
    const credit: ReceiptModel = { ...bill, balanceDue: '5000.00', changeAmount: '0' };
    expect(renderReceiptText(layoutReceipt(credit, 58), 58)).toContain(
      'Balance Due             5,000.00',
    );
  });

  it('centres the shop name allowing for double-width characters', () => {
    // "Sunrise Mobile" is 14 characters, so 28 columns of a 32 column roll.
    const first = printed(58).split('\n')[0];
    expect(first).toBe('  Sunrise Mobile');
  });
});

describe('byte framing', () => {
  const bytes = () => Array.from(encodeReceipt(bill, { width: 58, openDrawer: true }));

  it('starts by resetting the printer and selecting a code page', () => {
    expect(bytes().slice(0, 5)).toEqual([0x1b, 0x40, 0x1b, 0x74, 0x00]);
  });

  it('ends with a feed and a partial cut', () => {
    // ESC d 4 (feed four lines past the head), then GS V 1 (partial cut).
    expect(bytes().slice(-6)).toEqual([0x1b, 0x64, 0x04, 0x1d, 0x56, 0x01]);
  });

  it('kicks the drawer only when asked', () => {
    const withDrawer = Array.from(encodeReceipt(bill, { width: 58, openDrawer: true })).join(',');
    const without = Array.from(encodeReceipt(bill, { width: 58 })).join(',');
    expect(withDrawer).toContain('27,112,0,25,250');
    expect(without).not.toContain('27,112,0,25,250');
  });

  it('skips the cut when the caller turns it off', () => {
    const uncut = Array.from(encodeReceipt(bill, { width: 58, cut: false }));
    expect(uncut.slice(-2)).not.toEqual([0x1d, 0x56, 0x01]);
  });

  /**
   * No ESC/POS code page covers Sinhala, so these must not be emitted raw - a
   * printer fed those bytes prints noise or jams the parser.
   */
  it('replaces characters no code page can carry with a question mark', () => {
    expect(toBytes('Hello')).toEqual([72, 101, 108, 108, 111]);
    expect(toBytes('ලංකා')).toEqual([0x3f, 0x3f, 0x3f, 0x3f]);
    expect(Math.max(...toBytes('Café — 99°'))).toBeLessThanOrEqual(0x7e);
  });

  it('emits only bytes a printer can accept', () => {
    for (const byte of encodeReceipt(bill, { width: 80 })) {
      expect(byte).toBeGreaterThanOrEqual(0);
      expect(byte).toBeLessThanOrEqual(0xff);
    }
  });
});
