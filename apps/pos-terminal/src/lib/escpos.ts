/**
 * ESC/POS encoding for 58mm and 80mm thermal printers.
 *
 * Browser printing goes through window.print() and the @page rules, which needs
 * the printer installed as an OS print destination and shows a dialog. Thermal
 * printers are normally driven instead by writing ESC/POS bytes straight at them,
 * which prints silently and can kick the cash drawer. That transport only exists
 * in the desktop (Tauri) build - see lib/print.ts - but the encoding is the part
 * that is easy to get wrong, so it lives here on its own and is unit tested.
 *
 * The layout is deliberately kept as data: layoutReceipt() turns a bill into
 * aligned text blocks, renderReceiptText() is what those blocks look like on
 * paper, and encodeBlocks() adds the control codes. Tests assert the text, which
 * is readable, rather than byte soup.
 */

/** Paper width in millimetres. These are the two sizes the shops actually use. */
export type ThermalWidth = 58 | 80;

/**
 * Characters per line in Font A. A 58mm roll gives 32 and an 80mm roll 48; every
 * width calculation here is in characters, never millimetres.
 */
const COLUMNS: Record<ThermalWidth, number> = { 58: 32, 80: 48 };

export function columnsFor(width: ThermalWidth): number {
  return COLUMNS[width];
}

export type BlockStyle = 'normal' | 'bold' | 'title';
export type BlockAlign = 'left' | 'center' | 'right';

export interface Block {
  text: string;
  align: BlockAlign;
  style: BlockStyle;
}

export interface ReceiptLine {
  name: string;
  quantity: number | string;
  unitPrice: number | string;
  amount: number | string;
}

export interface ReceiptPayment {
  method: string;
  amount: number | string;
}

export interface ReceiptModel {
  businessName: string;
  addressLines: string[];
  phone?: string | null;
  /** Printed only when the shop is VAT registered, matching the A4 invoice. */
  vatTin?: string | null;
  billNumber: string;
  billedAt: string;
  cashierName?: string | null;
  customerName?: string | null;
  currency: string;
  lines: ReceiptLine[];
  subtotal: number | string;
  discountTotal: number | string;
  taxTotal: number | string;
  grandTotal: number | string;
  balanceDue: number | string;
  payments: ReceiptPayment[];
  changeAmount: number | string;
  footer?: string | null;
}

export interface EncodeOptions {
  width: ThermalWidth;
  /** Send the drawer-kick pulse. Only meaningful when a drawer is wired to the printer. */
  openDrawer?: boolean;
  cut?: boolean;
  /** Blank lines fed before the cut so the tear-off clears the print head. */
  feedBeforeCut?: number;
}

const num = (value: number | string | null | undefined): number => {
  const n = typeof value === 'string' ? Number(value) : (value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

/**
 * Amounts are formatted here rather than with Intl currency formatting, which
 * varies by the machine's locale and would make the same bill print differently
 * on two tills. The currency is named once beside the total instead.
 */
export function amount(value: number | string | null | undefined): string {
  const n = num(value);
  // toFixed(2) always produces a decimal point, so slicing around it is safe and
  // avoids destructuring a split that the compiler cannot prove is non-empty.
  const fixed = Math.abs(n).toFixed(2);
  const dot = fixed.indexOf('.');
  const grouped = fixed.slice(0, dot).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${n < 0 ? '-' : ''}${grouped}.${fixed.slice(dot + 1)}`;
}

/** Quantities print as integers when they are whole, so "2" not "2.000". */
export function quantity(value: number | string | null | undefined): string {
  const n = num(value);
  return Number.isInteger(n) ? String(n) : String(Number(n.toFixed(3)));
}

/** Greedy word wrap. A word longer than the line is hard-split rather than dropped. */
export function wrap(text: string, width: number): string[] {
  if (width <= 0) return [text];
  const out: string[] = [];
  for (const paragraph of String(text ?? '').split('\n')) {
    let line = '';
    for (const word of paragraph.split(/\s+/).filter(Boolean)) {
      let candidate = word;
      while (candidate.length > width) {
        if (line) {
          out.push(line);
          line = '';
        }
        out.push(candidate.slice(0, width));
        candidate = candidate.slice(width);
      }
      if (!line) {
        line = candidate;
      } else if (line.length + 1 + candidate.length <= width) {
        line += ` ${candidate}`;
      } else {
        out.push(line);
        line = candidate;
      }
    }
    out.push(line);
  }
  return out.length ? out : [''];
}

/**
 * A label on the left and a figure on the right on one line. The figure is never
 * truncated - a total that loses a digit is worse than a label that does - so the
 * label gives way first.
 */
export function twoColumn(left: string, right: string, width: number): string {
  const figure = String(right ?? '');
  if (figure.length >= width) return figure.slice(-width);
  const room = width - figure.length - 1;
  const label = String(left ?? '');
  const trimmed = label.length > room ? label.slice(0, room) : label;
  return trimmed + ' '.repeat(width - trimmed.length - figure.length) + figure;
}

const divider = (width: number) => '-'.repeat(width);

/** Turns a bill into the blocks that will be printed, in order. */
export function layoutReceipt(receipt: ReceiptModel, width: ThermalWidth): Block[] {
  const cols = columnsFor(width);
  const blocks: Block[] = [];
  const push = (text: string, align: BlockAlign = 'left', style: BlockStyle = 'normal') =>
    blocks.push({ text, align, style });

  // Double-width characters cover two columns, so the name wraps at half the line.
  for (const line of wrap(receipt.businessName, Math.floor(cols / 2))) {
    push(line, 'center', 'title');
  }
  for (const address of receipt.addressLines.filter(Boolean)) {
    for (const line of wrap(address, cols)) push(line, 'center');
  }
  if (receipt.phone) push(`Tel: ${receipt.phone}`, 'center');
  if (receipt.vatTin) push(`VAT Reg: ${receipt.vatTin}`, 'center');

  push(divider(cols));
  push(twoColumn('Bill', receipt.billNumber, cols));
  push(twoColumn('Date', formatBilledAt(receipt.billedAt), cols));
  if (receipt.cashierName) push(twoColumn('Cashier', receipt.cashierName, cols));
  if (receipt.customerName) {
    for (const line of wrap(`Customer: ${receipt.customerName}`, cols)) push(line);
  }
  push(divider(cols));

  for (const line of receipt.lines) {
    for (const part of wrap(line.name, cols)) push(part);
    push(
      twoColumn(
        `  ${quantity(line.quantity)} x ${amount(line.unitPrice)}`,
        amount(line.amount),
        cols,
      ),
    );
  }

  push(divider(cols));
  push(twoColumn('Subtotal', amount(receipt.subtotal), cols));
  if (num(receipt.discountTotal) > 0) {
    push(twoColumn('Discount', `-${amount(receipt.discountTotal)}`, cols));
  }
  if (num(receipt.taxTotal) > 0) {
    push(twoColumn('Tax', amount(receipt.taxTotal), cols));
  }
  push(twoColumn(`TOTAL ${receipt.currency}`, amount(receipt.grandTotal), cols), 'left', 'bold');

  if (receipt.payments.length) {
    push(divider(cols));
    for (const payment of receipt.payments) {
      push(twoColumn(payment.method, amount(payment.amount), cols));
    }
  }
  if (num(receipt.changeAmount) > 0) {
    push(twoColumn('Change', amount(receipt.changeAmount), cols));
  }
  if (num(receipt.balanceDue) > 0) {
    push(twoColumn('Balance Due', amount(receipt.balanceDue), cols), 'left', 'bold');
  }

  if (receipt.footer) {
    push(divider(cols));
    for (const line of wrap(receipt.footer, cols)) push(line, 'center');
  }
  return blocks;
}

function formatBilledAt(iso: string): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())} ` +
    `${pad(at.getHours())}:${pad(at.getMinutes())}`
  );
}

/** What the blocks look like on paper. Used by the tests and the on-screen preview. */
export function renderReceiptText(blocks: Block[], width: ThermalWidth): string {
  const cols = columnsFor(width);
  return blocks
    .map((block) => {
      // A title prints double width, so it occupies two columns per character.
      const span = block.style === 'title' ? block.text.length * 2 : block.text.length;
      if (block.align === 'center') {
        return ' '.repeat(Math.max(0, Math.floor((cols - span) / 2))) + block.text;
      }
      if (block.align === 'right') {
        return ' '.repeat(Math.max(0, cols - span)) + block.text;
      }
      return block.text;
    })
    .join('\n');
}

// --- byte framing ----------------------------------------------------------

const ESC = 0x1b;
const GS = 0x1d;
const LF = 0x0a;

const ALIGN: Record<BlockAlign, number> = { left: 0, center: 1, right: 2 };

/**
 * ESC/POS carries a single byte per character from a selected code page, and no
 * code page covers Sinhala or Tamil. Those would print as garbage, so anything
 * outside printable ASCII becomes '?' - visibly wrong on the receipt rather than
 * silently wrong. A shop needing Sinhala receipts has to use the browser/driver
 * path, which rasterises the text instead of sending characters.
 */
export function toBytes(text: string): number[] {
  const out: number[] = [];
  for (const char of text) {
    const code = char.codePointAt(0) ?? 0x3f;
    out.push(code >= 0x20 && code <= 0x7e ? code : 0x3f);
  }
  return out;
}

export function encodeBlocks(blocks: Block[], options: EncodeOptions): Uint8Array {
  const bytes: number[] = [];
  const feed = options.feedBeforeCut ?? 4;

  bytes.push(ESC, 0x40); // ESC @ - reset to a known state
  bytes.push(ESC, 0x74, 0x00); // ESC t 0 - code page 437

  let align = 0;
  let bold = false;
  let size = 0;
  for (const block of blocks) {
    const wantAlign = ALIGN[block.align];
    if (wantAlign !== align) {
      bytes.push(ESC, 0x61, wantAlign); // ESC a n
      align = wantAlign;
    }
    const wantBold = block.style === 'bold' || block.style === 'title';
    if (wantBold !== bold) {
      bytes.push(ESC, 0x45, wantBold ? 1 : 0); // ESC E n
      bold = wantBold;
    }
    const wantSize = block.style === 'title' ? 0x11 : 0x00; // double height + width
    if (wantSize !== size) {
      bytes.push(GS, 0x21, wantSize); // GS ! n
      size = wantSize;
    }
    for (const byte of toBytes(block.text)) bytes.push(byte);
    bytes.push(LF);
  }

  // Leave the printer as it was found, so the next job does not inherit bold.
  if (size !== 0) bytes.push(GS, 0x21, 0x00);
  if (bold) bytes.push(ESC, 0x45, 0x00);
  if (align !== 0) bytes.push(ESC, 0x61, 0x00);

  if (options.openDrawer) {
    // ESC p 0 t1 t2 - pulse pin 2. 50ms on, 250ms off suits the usual solenoid.
    bytes.push(ESC, 0x70, 0x00, 0x19, 0xfa);
  }
  if (options.cut !== false) {
    bytes.push(ESC, 0x64, feed); // ESC d n - feed past the head before cutting
    bytes.push(GS, 0x56, 0x01); // GS V 1 - partial cut, leaving a tab
  }
  return Uint8Array.from(bytes);
}

/** The whole job: bill in, printable bytes out. */
export function encodeReceipt(receipt: ReceiptModel, options: EncodeOptions): Uint8Array {
  return encodeBlocks(layoutReceipt(receipt, options.width), options);
}
