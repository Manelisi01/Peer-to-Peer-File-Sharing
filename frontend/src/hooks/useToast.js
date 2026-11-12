import { useCallback, useEffect, useState } from 'react';

export function useToast(duration) {
  if (duration === undefined) {
    duration = 3600;
  }

  const [toast, setToast] = useState(null);

  useEffect(function () {
    if (!toast) {
      return undefined;
    }

    const timer = window.setTimeout(function () {
      setToast(null);
    }, duration);

    return function () {
      window.clearTimeout(timer);
    };
  }, [toast, duration]);

  const notify = useCallback(function (message, tone) {
    if (tone === undefined) {
      tone = 'success';
    }
    setToast({ message, tone });
  }, []);

  const dismiss = useCallback(function () {
    setToast(null);
  }, []);

  return { toast, notify, dismiss };
}
