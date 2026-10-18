import { startTransition, useState } from 'react';
import { openwireApi } from '../api/openwireApi';
import { isValidSha256 } from '../utils/format';

export function usePeerSearch({ notify }) {
  const [hashInput, setHashInputRaw] = useState('');
  const [peers, setPeers] = useState([]);
  const [searchDone, setSearchDone] = useState(false);
  const [searching, setSearching] = useState(false);

  const reset = () => {
    setPeers([]);
    setSearchDone(false);
  };

  const setHashInput = (value) => {
    setHashInputRaw(value);
    setSearchDone(false);
  };

  const search = async (event) => {
    event?.preventDefault();
    const hash = hashInput.trim();
    if (!isValidSha256(hash)) {
      notify('Enter a 64-character SHA-256 hash', 'error');
      return;
    }
    setSearching(true);
    try {
      const found = await openwireApi.findPeers(hash);
      startTransition(() => setPeers(found));
      setSearchDone(true);
      notify(`${found.length} peer${found.length === 1 ? '' : 's'} found`);
    } catch (error) {
      notify(error.message || 'Peer lookup failed', 'error');
    } finally {
      setSearching(false);
    }
  };

  const useHash = (hash) => {
    setHashInputRaw(hash);
    reset();
    notify('File hash ready for peer lookup');
  };

  return { hashInput, setHashInput, peers, searchDone, searching, search, useHash, reset };
}
