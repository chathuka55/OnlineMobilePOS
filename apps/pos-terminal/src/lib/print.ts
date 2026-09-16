export type ReceiptFormat = 'thermal80' | 'half-a4' | 'a4';

const PAGE_SIZES: Record<ReceiptFormat, string> = {
  thermal80: '80mm auto',
  'half-a4': '148mm 210mm',
  a4: '210mm 297mm',
};

const STORAGE_KEY = 'pos_receipt_format';

export function getStoredReceiptFormat(): ReceiptFormat {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === 'half-a4' || stored === 'a4' || stored === 'thermal80' ? stored : 'thermal80';
}

export function setStoredReceiptFormat(format: ReceiptFormat) {
  localStorage.setItem(STORAGE_KEY, format);
}

/**
 * Prints the hidden #receipt-print element (see components/Receipt.tsx and the
 * @media print rules in styles.css) at the given paper size. Browsers only expose
 * printing through window.print() and the @page CSS at-rule, so a physical thermal
 * printer needs to already be set up as the OS's print destination for this format
 * to reach real 80mm paper - there's no raw ESC/POS output here.
 */
export function printReceipt(title: string, format: ReceiptFormat = getStoredReceiptFormat()) {
  let styleTag = document.getElementById('print-page-size') as HTMLStyleElement | null;
  if (!styleTag) {
    styleTag = document.createElement('style');
    styleTag.id = 'print-page-size';
    document.head.appendChild(styleTag);
  }
  styleTag.textContent = `@page { size: ${PAGE_SIZES[format]}; margin: ${format === 'thermal80' ? '0' : '10mm'}; }`;
  document.body.setAttribute('data-print-format', format);

  const previous = document.title;
  document.title = title;
  window.print();
  document.title = previous;
}
