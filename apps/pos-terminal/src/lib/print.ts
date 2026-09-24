import { encodeReceipt, type EncodeOptions, type ReceiptModel } from './escpos';

export type ReceiptFormat = 'thermal58' | 'thermal80' | 'half-a4' | 'a4';

const PAGE_SIZES: Record<ReceiptFormat, string> = {
  thermal58: '58mm auto',
  thermal80: '80mm auto',
  'half-a4': '148mm 210mm',
  a4: '210mm 297mm',
};

const STORAGE_KEY = 'pos_receipt_format';
const PRINTER_KEY = 'pos_thermal_printer';
const DRAWER_KEY = 'pos_kick_drawer';

const FORMATS: ReceiptFormat[] = ['thermal58', 'thermal80', 'half-a4', 'a4'];

export function getStoredReceiptFormat(): ReceiptFormat {
  const stored = localStorage.getItem(STORAGE_KEY) as ReceiptFormat | null;
  return stored && FORMATS.includes(stored) ? stored : 'thermal80';
}

export function setStoredReceiptFormat(format: ReceiptFormat) {
  localStorage.setItem(STORAGE_KEY, format);
}

/** Which printer raw jobs go to. Empty means the OS default. */
export function getStoredPrinterName(): string {
  return localStorage.getItem(PRINTER_KEY) ?? '';
}

export function setStoredPrinterName(name: string) {
  localStorage.setItem(PRINTER_KEY, name);
}

export function getStoredDrawerKick(): boolean {
  return localStorage.getItem(DRAWER_KEY) === 'true';
}

export function setStoredDrawerKick(enabled: boolean) {
  localStorage.setItem(DRAWER_KEY, String(enabled));
}

export function isThermal(format: ReceiptFormat): boolean {
  return format === 'thermal58' || format === 'thermal80';
}

export function thermalWidth(format: ReceiptFormat): 58 | 80 {
  return format === 'thermal58' ? 58 : 80;
}

/**
 * Prints the hidden #receipt-print element (see components/Receipt.tsx and the
 * @media print rules in styles.css) at the given paper size. Browsers only expose
 * printing through window.print() and the @page at-rule, so the thermal printer
 * has to be installed as an OS print destination and the cashier sees a dialog.
 * This is the only path available in the browser build; the desktop build can use
 * printReceiptRaw() instead.
 */
export function printReceipt(title: string, format: ReceiptFormat = getStoredReceiptFormat()) {
  let styleTag = document.getElementById('print-page-size') as HTMLStyleElement | null;
  if (!styleTag) {
    styleTag = document.createElement('style');
    styleTag.id = 'print-page-size';
    document.head.appendChild(styleTag);
  }
  styleTag.textContent = `@page { size: ${PAGE_SIZES[format]}; margin: ${
    isThermal(format) ? '0' : '10mm'
  }; }`;
  document.body.setAttribute('data-print-format', format);

  const previous = document.title;
  document.title = title;
  window.print();
  document.title = previous;
}

// --- raw ESC/POS, desktop only ---------------------------------------------

/**
 * Tauri exposes its command bridge on the window object. It is read through this
 * narrow shape rather than by depending on @tauri-apps/api so that the browser
 * build - which is what is deployed today - pulls in no desktop code at all and
 * still type checks.
 */
type TauriBridge = { invoke: (cmd: string, args?: Record<string, unknown>) => Promise<unknown> };

function bridge(): TauriBridge | null {
  const candidate = (globalThis as { __TAURI_INTERNALS__?: TauriBridge }).__TAURI_INTERNALS__;
  return candidate && typeof candidate.invoke === 'function' ? candidate : null;
}

/** True when running inside the desktop shell, where raw printing is possible. */
export function canPrintRaw(): boolean {
  return bridge() !== null;
}

export class RawPrintUnavailableError extends Error {
  constructor() {
    super(
      'Raw thermal printing needs the desktop app. In a browser the receipt is ' +
        'printed through the operating system printer instead.',
    );
    this.name = 'RawPrintUnavailableError';
  }
}

/**
 * Sends the receipt to the printer as ESC/POS bytes: no dialog, and the cash
 * drawer can be kicked at the same time. Throws RawPrintUnavailableError in the
 * browser, so callers can fall back to printReceipt().
 */
export async function printReceiptRaw(
  receipt: ReceiptModel,
  options: EncodeOptions & { printerName?: string },
): Promise<void> {
  const tauri = bridge();
  if (!tauri) throw new RawPrintUnavailableError();
  const bytes = encodeReceipt(receipt, options);
  await tauri.invoke('print_raw', {
    printerName: options.printerName ?? getStoredPrinterName(),
    // Tauri decodes a JSON array of bytes into Vec<u8> on the Rust side.
    data: Array.from(bytes),
  });
}

/** Names of the printers the desktop shell can see, for the settings dropdown. */
export async function listPrinters(): Promise<string[]> {
  const tauri = bridge();
  if (!tauri) return [];
  const result = await tauri.invoke('list_printers');
  return Array.isArray(result) ? result.map(String) : [];
}

/**
 * Prints the receipt the best way this build can: raw ESC/POS when running on the
 * desktop and a thermal size is selected, and the browser dialog otherwise. The
 * fallback is deliberate - a cashier who cannot print is a stopped queue, so a
 * failure in the raw path still produces a receipt.
 */
export async function printBestEffort(
  title: string,
  receipt: ReceiptModel,
  format: ReceiptFormat = getStoredReceiptFormat(),
): Promise<'raw' | 'browser'> {
  if (isThermal(format) && canPrintRaw()) {
    try {
      await printReceiptRaw(receipt, {
        width: thermalWidth(format),
        openDrawer: getStoredDrawerKick(),
      });
      return 'raw';
    } catch (error) {
      console.warn('Raw thermal print failed; falling back to the print dialog', error);
    }
  }
  printReceipt(title, format);
  return 'browser';
}
