import { useCallback, useEffect, useState } from 'react';

export function useToast(duration = 3600) {
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), duration);
    return () => window.clearTimeout(timer);
  }, [toast, duration]);

  const notify = useCallback((message, tone = 'success') => setToast({ message, tone }), []);
  const dismiss = useCallback(() => setToast(null), []);
  return { toast, notify, dismiss };
}
