import { useCallback, useEffect, useState } from 'react';
import { openwireApi } from '../api/openwireApi';

const defaults = { trackerHost: '127.0.0.1', trackerPort: 5000, peerIp: '127.0.0.1', peerPort: 7000 };
const hasNetworkAddress = (peerIp) => Boolean(peerIp && peerIp !== '127.0.0.1' && peerIp !== '0.0.0.0');
const saved = () => {
  try { return { ...defaults, ...JSON.parse(localStorage.getItem('openwire-network') || '{}') }; }
  catch { return defaults; }
};

export function useNetworkSettings() {
  const [network, setNetwork] = useState(saved);
  const [connected, setConnected] = useState(false);

  const refreshNetwork = useCallback(async () => {
    try {
      const { peerIp } = await openwireApi.getNetworkInfo();
      setConnected(hasNetworkAddress(peerIp));
      setNetwork(current => {
        if (!hasNetworkAddress(peerIp) || current.peerIp !== '127.0.0.1') return current;
        const next = {
          ...current,
          peerIp,
          trackerHost: current.trackerHost === '127.0.0.1' ? peerIp : current.trackerHost,
        };
        localStorage.setItem('openwire-network', JSON.stringify(next));
        return next;
      });
    } catch {
      setConnected(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    const refresh = () => {
      if (active) refreshNetwork();
    };
    refresh();
    const interval = window.setInterval(refresh, 12000);
    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [refreshNetwork]);

  const updateField = (field, value) => setNetwork(current => {
    const next = { ...current, [field]: value };
    localStorage.setItem('openwire-network', JSON.stringify(next));
    return next;
  });
  return { network, updateField, connected, refreshNetwork };
}