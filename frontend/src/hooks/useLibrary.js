import { useCallback, useEffect, useState } from 'react';
import { openwireApi } from '../api/openwireApi';
import { isDesktop, localPathOf } from '../services/desktop';

export function useLibrary(options) {
  const { network, notify } = options;

  const [server, setServer] = useState(null);
  const [files, setFiles] = useState([]);
  const [busy, setBusy] = useState('');

  const load = useCallback(async function () {
    const statusResult = await openwireApi.getStatus();
    const seeders = await openwireApi.getSeeders();

    setServer(statusResult);
    setFiles(function (current) {
      const seededNames = new Set();
      for (let i = 0; i < seeders.length; i += 1) {
        seededNames.add(seeders[i].fileName);
      }

      const waiting = current.filter(function (file) {
        return !file.seeded && !seededNames.has(file.fileName);
      });

      const seededList = seeders.map(function (file) {
        return Object.assign({}, file, { seeded: true });
      });

      return seededList.concat(waiting);
    });
  }, []);

  useEffect(function () {
    function tick() {
      load().catch(function () {
        setServer({ status: 'DOWN', sharedFiles: 0 });
      });
    }

    tick();
    const interval = window.setInterval(tick, 12000);
    return function () {
      window.clearInterval(interval);
    };
  }, [load]);

  const refresh = useCallback(async function () {
    try {
      await load();
      return true;
    } catch (error) {
      setServer({ status: 'DOWN', sharedFiles: 0 });
      notify(error.message || 'Could not refresh node status', 'error');
      return false;
    }
  }, [load, notify]);

  async function run(busyKey, fallbackError, action) {
    setBusy(busyKey);
    try {
      return await action();
    } catch (error) {
      if (error && error.message) {
        notify(error.message, 'error');
      } else {
        notify(fallbackError, 'error');
      }
    } finally {
      setBusy('');
    }
  }

  function uploadSelected(selected) {
    return run('upload', 'Upload failed', async function () {
      const results = await Promise.allSettled(selected.map(function (file) {
        return openwireApi.uploadFile(file);
      }));

      const uploaded = [];
      const failed = [];

      for (let i = 0; i < results.length; i += 1) {
        if (results[i].status === 'fulfilled') {
          uploaded.push(results[i].value);
        } else {
          failed.push(results[i].reason);
        }
      }

      if (uploaded.length === 0) {
        throw failed[0] || new Error('Upload failed');
      }

      setFiles(function (current) {
        const added = uploaded.map(function (file) {
          return Object.assign({}, file, {
            seeded: false,
            peerIp: network.peerIp,
            peerPort: network.peerPort
          });
        });

        return added.concat(current);
      });

      if (failed.length > 0) {
        notify(uploaded.length + ' uploaded; ' + failed.length + ' failed', 'error');
      } else {
        if (uploaded.length === 1) {
          notify('1 file added to storage');
        } else {
          notify(uploaded.length + ' files added to storage');
        }
      }

      return uploaded;
    });
  }

  function addLocalFiles(selected) {
    return run('upload', 'Could not add files', async function () {
      const added = [];
      let firstError = null;
      let failed = 0;

      for (let i = 0; i < selected.length; i += 1) {
        const file = selected[i];
        const path = localPathOf(file);

        if (!path) {
          failed += 1;
          if (!firstError) {
            firstError = new Error('Could not read the location of this file');
          }
          continue;
        }

        try {
          const info = await openwireApi.hashLocalFile(path);
          added.push(Object.assign({}, info, {
            path,
            managed: false,
            seeded: false,
            peerIp: network.peerIp,
            peerPort: network.peerPort
          }));
        } catch (error) {
          failed += 1;
          if (!firstError) {
            firstError = error;
          }
        }
      }

      if (added.length === 0) {
        throw firstError || new Error('Could not add files');
      }

      const known = new Set();
      for (let j = 0; j < files.length; j += 1) {
        known.add(files[j].fileHash);
      }

      const fresh = added.filter(function (file) {
        return !known.has(file.fileHash);
      });

      setFiles(function (current) {
        return fresh.concat(current);
      });

      if (failed > 0) {
        notify(added.length + ' added; ' + failed + ' failed', 'error');
      } else if (added.length === 1) {
        notify('1 file added (shared from where it is)');
      } else {
        notify(added.length + ' files added (shared from where they are)');
      }

      return fresh;
    });
  }

  function addFiles(fileList) {
    const selected = Array.from(fileList || []);
    if (selected.length === 0) {
      return Promise.resolve();
    }
    if (isDesktop) {
      return addLocalFiles(selected);
    }
    return uploadSelected(selected);
  }

  function startSeeding(file) {
    return run('seed-' + file.fileName, 'Could not start seeding', async function () {
      if (file.path) {
        await openwireApi.startSeedingLocal(file.path, network);
      } else {
        await openwireApi.startSeeding(file.fileName, network);
      }

      await refresh();
      notify(file.fileName + ' is now being shared');
    });
  }

  function stopSharingInPlace(file) {
    if (file.seeded && !window.confirm('Stop sharing ' + file.fileName + '? The file itself is not deleted.')) {
      return Promise.resolve(false);
    }

    return run('delete-' + file.fileName, 'Could not stop sharing this file', async function () {
      if (file.seeded) {
        await openwireApi.stopSharing(file.fileHash);
      }

      setFiles(function (current) {
        return current.filter(function (item) {
          return item.fileHash !== file.fileHash;
        });
      });

      await refresh();
      notify(file.fileName + ' removed from your shared files');
      return true;
    });
  }

  function deleteFile(file) {
    if (file.managed === false) {
      return stopSharingInPlace(file);
    }

    const confirmed = window.confirm('Delete ' + file.fileName + ' from local storage and stop seeding it?');
    if (!confirmed) {
      return Promise.resolve(false);
    }

    return run('delete-' + file.fileName, 'Could not delete this file', async function () {
      await openwireApi.deleteFile(file.fileName);
      setFiles(function (current) {
        return current.filter(function (item) {
          return item.fileName !== file.fileName;
        });
      });
      await refresh();
      notify(file.fileName + ' deleted from this node');
      return true;
    });
  }

  return {
    server: server,
    files: files,
    busy: busy,
    online: server && server.status === 'UP',
    refresh: refresh,
    addFiles: addFiles,
    startSeeding: startSeeding,
    deleteFile: deleteFile
  };
}
