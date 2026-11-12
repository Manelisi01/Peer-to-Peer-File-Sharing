import { useCallback, useEffect, useState } from 'react';
import { openwireApi } from '../api/openwireApi';

const defaults = { trackerHost: '127.0.0.1', trackerPort: 5000, peerIp: '127.0.0.1', peerPort: 7000 };

function hasNetworkAddress(peerIp) {
  return Boolean(peerIp && peerIp !== '127.0.0.1' && peerIp !== '0.0.0.0');
}

function saved() {
  try {
    const stored = localStorage.getItem('openwire-network') || '{}';
    return Object.assign({}, defaults, JSON.parse(stored));
  } catch (error) {
    return defaults;
  }
}

export function useNetworkSettings() {
  const [network, setNetwork] = useState(saved);
  const [connected, setConnected] = useState(false);
  const [trackerConnected, setTrackerConnected] = useState(false);

  const refreshNetwork = useCallback(async function () {
    try {
      const results = await Promise.all([
        openwireApi.getNetworkInfo(),
        openwireApi.getTrackerStatus(network)
      ]);
      const info = results[0];
      const peerIp = info.peerIp;

      setConnected(hasNetworkAddress(peerIp));
      setTrackerConnected(Boolean(results[1].connected));
      setNetwork(function (current) {
        if (!hasNetworkAddress(peerIp) || current.peerIp !== '127.0.0.1') {
          return current;
        }

        const next = Object.assign({}, current, {
          peerIp,
          trackerHost: current.trackerHost === '127.0.0.1' ? peerIp : current.trackerHost
        });

        localStorage.setItem('openwire-network', JSON.stringify(next));
        return next;
      });
      return true;
    } catch (error) {
      setConnected(false);
      setTrackerConnected(false);
      return false;
    }
  }, [network.trackerHost, network.trackerPort]);

  useEffect(function () {
    let active = true;

    function refresh() {
      if (active) {
        refreshNetwork();
      }
    }

    refresh();
    const interval = window.setInterval(refresh, 12000);

    return function () {
      active = false;
      window.clearInterval(interval);
    };
  }, [refreshNetwork]);

  function updateField(field, value) {
    setNetwork(function (current) {
      const next = Object.assign({}, current, {});
      next[field] = value;
      localStorage.setItem('openwire-network', JSON.stringify(next));
      return next;
    });
  }

  return {
    network,
    updateField,
    connected,
    trackerConnected,
    refreshNetwork
  };
}