import { Check, CircleAlert, X } from 'lucide-react';

export default function Toast({ toast, onDismiss }) {
  if (!toast) return null;
  return (
    <div className={`toast toast-${toast.tone}`} role="status">
      {toast.tone === 'error' ? <CircleAlert size={16} /> : <Check size={16} />}
      {toast.message}
      <button onClick={onDismiss} aria-label="Dismiss notification"><X size={14} /></button>
    </div>
  );
}
