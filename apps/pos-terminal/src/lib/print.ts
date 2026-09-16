/**
 * Receipt print stub.
 * Browser: window.print()
 * Desktop: wire @tauri-apps/plugin-shell or a custom ESC/POS plugin later.
 */
export function printReceipt(title = 'Easy POS Receipt') {
  // Future: invoke('print_escpos', { payload }) via Tauri command / plugin
  // await invoke('plugin:escpos|print', { ... })
  const previous = document.title;
  document.title = title;
  window.print();
  document.title = previous;
}
