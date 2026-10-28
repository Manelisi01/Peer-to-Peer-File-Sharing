import { useEffect, useState } from 'react';
import { request } from '../api/httpClient';

const defaults = { trackerHost: '127.0.0.1', trackerPort: 5000, peerIp: '127.0.0.1', peerPort: 7000 };
const saved = () => {
  try { return { ...defaults, ...JSON.parse(localStorage.getItem('openwire-network') || '{}') }; }
  catch { return defaults; }
};

export function useNetworkSettings() {
  const [network, setNetwork] = useState(saved);
  useEffect(() => {
    let active = true;
    request('/api/network-info').then(({ peerIp }) => {
      if (!active || !peerIp || peerIp === '127.0.0.1') return;
      setNetwork(current => {
        // Only replace loopback; never overwrite an address the user selected.
        const next = { ...current, peerIp: current.peerIp === '127.0.0.1' ? peerIp : current.peerIp,
          trackerHost: current.trackerHost === '127.0.0.1' ? peerIp : current.trackerHost };
        localStorage.setItem('openwire-network', JSON.stringify(next));
        return next;
      });
    }).catch(() => {});
    return () => { active = false; };
  }, []);
  const updateField = (field, value) => setNetwork(current => {
    const next = { ...current, [field]: value };
    localStorage.setItem('openwire-network', JSON.stringify(next));
    return next;
  });
  return { network, updateField };
}
