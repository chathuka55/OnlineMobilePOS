import { useEffect, useRef, useState } from 'react';
import type { Customer, CustomerType } from '@possaas/api-client';
import { Input } from '@possaas/ui';
import { api } from '../lib/api';

export function CustomerSearch({
  type,
  placeholder,
  onSelect,
}: {
  type?: CustomerType;
  placeholder?: string;
  onSelect: (customer: Customer) => void;
}) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Customer[]>([]);
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    const handle = setTimeout(() => {
      api.customers
        .list({ q: query.trim(), size: 8, type })
        .then((data) => {
          const rows = Array.isArray(data) ? data : data.content;
          setResults(rows);
          setOpen(true);
        })
        .catch(() => setResults([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [query, type]);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  return (
    <div ref={boxRef} className="relative">
      <Input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => results.length > 0 && setOpen(true)}
        placeholder={placeholder ?? 'Search customer by name or phone…'}
        autoComplete="off"
      />
      {open && results.length > 0 && (
        <ul className="absolute z-20 mt-1 max-h-60 w-full overflow-y-auto rounded-lg border bg-white shadow-lg">
          {results.map((c) => (
            <li key={c.id}>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-100"
                onClick={() => {
                  onSelect(c);
                  setQuery(c.displayName);
                  setOpen(false);
                }}
              >
                <span className="text-navy font-medium">{c.displayName}</span>
                <span className="text-muted-foreground ml-2 text-xs">
                  {c.phonePrimary || c.code || ''}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
