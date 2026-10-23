import { useCallback, useEffect, useState } from 'react';
import { openwireApi } from '../api/openwireApi';
import { isDesktop, localPathOf } from '../services/desktop';

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

  // Browser: copies the files into the node's storage folder.
  const uploadSelected = (selected) =>
    run('upload', 'Upload failed', async () => {
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

  // Desktop app: files stay where they are. Only their hash is computed, nothing is copied.
  const addLocalFiles = (selected) =>
    run('upload', 'Could not add files', async () => {
      const added = [];
      let firstError = null;
      let failed = 0;

      // One at a time: hashing big files in parallel would just thrash the disk.
      for (const file of selected) {
        const path = localPathOf(file);
        if (!path) {
          failed += 1;
          firstError = firstError || new Error('Could not read the location of this file');
          continue;
        }
        try {
          const info = await openwireApi.hashLocalFile(path);
          added.push({ ...info, path, managed: false, seeded: false, peerIp: network.peerIp, peerPort: network.peerPort });
        } catch (error) {
          failed += 1;
          firstError = firstError || error;
        }
      }

      if (added.length === 0) throw firstError || new Error('Could not add files');

      // Skip files that are already in the list (same content).
      const known = new Set(files.map((f) => f.fileHash));
      const fresh = added.filter((f) => !known.has(f.fileHash));
      setFiles((current) => [...fresh, ...current]);

      if (failed > 0) notify(`${added.length} added; ${failed} failed`, 'error');
      else notify(added.length === 1 ? '1 file added (shared from where it is)' : `${added.length} files added (shared from where they are)`);
      return fresh;
    });

  const addFiles = (fileList) => {
    const selected = Array.from(fileList || []);
    if (selected.length === 0) return Promise.resolve();
    return isDesktop ? addLocalFiles(selected) : uploadSelected(selected);
  };

  const startSeeding = (file) =>
    run(`seed-${file.fileName}`, 'Could not start seeding', async () => {
      if (file.path) await openwireApi.startSeedingLocal(file.path, network);
      else await openwireApi.startSeeding(file.fileName, network);
      await refresh();
      notify(`${file.fileName} is now being shared`);
    });

  // Files shared in place (desktop app) are only taken off the list; the original is never deleted.
  const stopSharingInPlace = (file) => {
    if (file.seeded && !window.confirm(`Stop sharing ${file.fileName}? The file itself is not deleted.`)) {
      return Promise.resolve(false);
    }
    return run(`delete-${file.fileName}`, 'Could not stop sharing this file', async () => {
      if (file.seeded) await openwireApi.stopSharing(file.fileHash);
      setFiles((current) => current.filter((f) => f.fileHash !== file.fileHash));
      await refresh();
      notify(`${file.fileName} removed from your shared files`);
      return true;
    });
  };

  const deleteFile = (file) => {
    if (file.managed === false) return stopSharingInPlace(file);

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
    refresh, addFiles, startSeeding, deleteFile,
  };
}
