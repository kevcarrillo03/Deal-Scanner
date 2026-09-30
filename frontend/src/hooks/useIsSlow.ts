import { useEffect, useState } from 'react';

export function useIsSlow(loading: boolean, afterMs = 5000) {
  const [isSlow, setIsSlow] = useState(false);

  useEffect(() => {
    setIsSlow(false);
    if (!loading) return;

    const timer = setTimeout(() => setIsSlow(true), afterMs);
    return () => clearTimeout(timer);
  }, [loading, afterMs]);

  return isSlow;
}
