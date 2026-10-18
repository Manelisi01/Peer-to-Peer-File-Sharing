import { useCallback, useEffect, useState } from 'react';
import { openwireApi } from '../api/openwireApi';

// Server status + local file library (upload / seed / delete).
// Reports outcomes through `notify` so it never touches UI directly.
export function useLibrary({ network, notify }) {
  const [server, setServer] = useState(null);
  const [files, setFiles] = useState([]);
  const [busy, setBusy] = useState('');

  const load = useCallback(async () => {
    const [status, seeders] = await Promise.all([openwireApi.getStatus(), openwireApi.getSeeders()]);
    setServer(status);
    setFiles((current) => {
      const seededNames = new Set(seeders.map((f) => f.fileName));
      const waiting = current.filter((f) => !f.seeded && !seededNames.has(f.fileName));
      return [...seeders.map((f) => ({ ...f, seeded: true })), ...waiting];
    });
  }, []);

  useEffect(() => {
    const tick = () => load().catch(() => setServer({ status: 'DOWN', sharedFiles: 0 }));
    tick();
    const interval = window.setInterval(tick, 12000);
    return () => window.clearInterval(interval);
  }, [load]);

  const refresh = useCallback(async () => {
    try {
      await load();
    } catch (error) {
      setServer({ status: 'DOWN', sharedFiles: 0 });
      notify(error.message || 'Could not refresh node status', 'error');
    }
  }, [load, notify]);

  // Wraps an async action with busy-state + error toast.
  const run = async (busyKey, fallbackError, action) => {
    setBusy(busyKey);
    try {
      return await action();
    } catch (error) {
      notify(error?.message || fallbackError, 'error');
    } finally {
      setBusy('');
    }
  };

  const uploadFiles = (fileList) => {
    const selected = Array.from(fileList || []);
    if (selected.length === 0) return Promise.resolve();

    return run('upload', 'Upload failed', async () => {
      const results = await Promise.allSettled(selected.map(openwireApi.uploadFile));
      const uploaded = results.filter((r) => r.status === 'fulfilled').map((r) => r.value);
      const failed = results.filter((r) => r.status === 'rejected');

      if (uploaded.length === 0) throw failed[0]?.reason || new Error('Upload failed');

      setFiles((current) => [
        ...uploaded.map((f) => ({ ...f, seeded: false, peerIp: network.peerIp, peerPort: network.peerPort })),
        ...current,
      ]);

      if (failed.length > 0) {
        notify(`${uploaded.length} uploaded; ${failed.length} failed`, 'error');
      } else {
        notify(uploaded.length === 1 ? '1 file added to storage' : `${uploaded.length} files added to storage`);
      }
      return uploaded;
    });
  };

  const startSeeding = (file) =>
    run(`seed-${file.fileName}`, 'Could not start seeding', async () => {
      await openwireApi.startSeeding(file.fileName, network);
      await refresh();
      notify(`${file.fileName} is now being shared`);
    });

  const deleteFile = (file) => {
    const confirmed = window.confirm(`Delete ${file.fileName} from local storage and stop seeding it?`);
    if (!confirmed) return Promise.resolve(false);

    return run(`delete-${file.fileName}`, 'Could not delete this file', async () => {
      await openwireApi.deleteFile(file.fileName);
      setFiles((current) => current.filter((f) => f.fileName !== file.fileName));
      await refresh();
      notify(`${file.fileName} deleted from this node`);
      return true;
    });
  };

  return {
    server, files, busy,
    online: server?.status === 'UP',
    refresh, uploadFiles, startSeeding, deleteFile,
  };
}
