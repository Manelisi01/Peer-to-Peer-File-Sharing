import React from 'react';
import { useState } from 'react';
import { openwireApi } from '../api/openwireApi';
import { isValidSha256 } from '../utils/format';

export function usePeerSearch(options) {
  const { notify, network } = options;

  const [hashInput, setHashInputRaw] = useState('');
  const [peers, setPeers] = useState([]);
  const [searchDone, setSearchDone] = useState(false);
  const [searching, setSearching] = useState(false);

  function reset() {
    setPeers([]);
    setSearchDone(false);
  }

  function setHashInput(value) {
    setHashInputRaw(value);
    setSearchDone(false);
  }

  async function search(event) {
    if (event && event.preventDefault) {
      event.preventDefault();
    }

    const hash = hashInput.trim();

    if (!isValidSha256(hash)) {
      notify('Enter a 64-character SHA-256 hash', 'error');
      return;
    }

    setSearching(true);

    try {
      const found = await openwireApi.findPeers(hash, network);
      setPeers(found);
      setSearchDone(true);

      if (found.length === 1) {
        notify('1 peer found');
      } else {
        notify(found.length + ' peers found');
      }
    } catch (error) {
      if (error && error.message) {
        notify(error.message, 'error');
      } else {
        notify('Peer lookup failed', 'error');
      }
    } finally {
      setSearching(false);
    }
  }

  function useHash(hash) {
    setHashInputRaw(hash);
    reset();
    notify('File hash ready for peer lookup');
  }

  return {
    hashInput,
    setHashInput,
    peers,
    searchDone,
    searching,
    search,
    useHash,
    reset
  };
}
