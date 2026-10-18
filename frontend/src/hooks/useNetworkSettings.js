import { useState } from 'react';

const initialNetwork = {
  trackerHost: '127.0.0.1',
  trackerPort: 5000,
  peerIp: '127.0.0.1',
  peerPort: 7000,
};

export function useNetworkSettings() {
  const [network, setNetwork] = useState(initialNetwork);
  const updateField = (field, value) => setNetwork((n) => ({ ...n, [field]: value }));
  return { network, updateField };
}
