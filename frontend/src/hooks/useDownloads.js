import { useEffect, useRef, useState } from 'react';
import { openwireApi } from '../api/openwireApi';
import { downloadStorage } from '../services/downloadStorage';
import { isValidSha256 } from '../utils/format';
import { isActiveJob } from '../utils/jobStatus';
import { saveCompletedDownload } from '../services/desktop';

export function useDownloads(options) {
  const { network, notify } = options;

  const [starting, setStarting] = useState(false);
  const savingIds = useRef(new Set());
  const savedIds = useRef(new Set());
  const cancelRequestedIds = useRef(new Set());
  const [cancelingIds, setCancelingIds] = useState([]);
  const [jobs, setJobs] = useState(function () {
    return downloadStorage.load();
  });

  const activeJobs = jobs.filter(isActiveJob);
  const activeJobIds = activeJobs.map(function (job) {
    return job.id;
  }).join('|');

  function commit(updater) {
    setJobs(function (current) {
      const next = updater(current);
      downloadStorage.save(next);
      return next;
    });
  }

  function saveCompletedJob(job) {
    if (savingIds.current.has(job.id) || savedIds.current.has(job.id)) {
      return;
    }

    savingIds.current.add(job.id);
    saveCompletedDownload(job.id, job.targetFile)
      .then(function (destination) {
        savedIds.current.add(job.id);
        notify('Saved ' + job.targetFile + ' to ' + destination);
      })
      .catch(function (error) {
        notify('Could not save ' + job.targetFile + ': ' + error.message, 'error');
      })
      .finally(function () {
        savingIds.current.delete(job.id);
      });
  }

  useEffect(function () {
    if (!activeJobIds) {
      return undefined;
    }

    const ids = activeJobIds.split('|');

    async function poll() {
      const updates = await Promise.all(
        ids.map(function (id) {
          return openwireApi.getDownload(id).catch(function () {
            return null;
          });
        })
      );

      for (let i = 0; i < updates.length; i += 1) {
        const job = updates[i];

        if (!job || cancelRequestedIds.current.has(job.id)
            || job.status !== 'COMPLETED' || savingIds.current.has(job.id) || savedIds.current.has(job.id)) {
          continue;
        }

        saveCompletedJob(job);
      }

      commit(function (current) {
        return current.map(function (jobInList) {
          for (let j = 0; j < updates.length; j += 1) {
            const update = updates[j];
            if (update && update.id === jobInList.id
                && !cancelRequestedIds.current.has(update.id)) {
              return update;
            }
          }
          return jobInList;
        });
      });
    }

    poll();
    const interval = window.setInterval(poll, 1400);
    return function () {
      window.clearInterval(interval);
    };
  }, [activeJobIds, notify]);

  function addJob(job) {
    commit(function (current) {
      const next = current.filter(function (item) {
        return item.id !== job.id;
      });
      return [job].concat(next);
    });
  }

  async function start(rawHash, rawTarget) {
    const fileHash = rawHash.trim();
    const targetFile = rawTarget.trim();

    if (!isValidSha256(fileHash)) {
      notify('Enter a valid SHA-256 hash before downloading', 'error');
      return false;
    }

    if (!targetFile) {
      notify('Choose a name for the downloaded file', 'error');
      return false;
    }

    setStarting(true);

    try {
      const job = await openwireApi.startDownload({ targetFile, fileHash }, network);
      addJob(job);
      notify('Download queued');
      return true;
    } catch (error) {
      notify(error.message || 'Could not start download', 'error');
      return false;
    } finally {
      setStarting(false);
    }
  }

  async function cancel(id) {
    if (cancelRequestedIds.current.has(id)) {
      return false;
    }

    cancelRequestedIds.current.add(id);
    setCancelingIds(function (current) {
      return current.concat(id);
    });

    try {
      const update = await openwireApi.cancelDownload(id);
      commit(function (current) {
        return current.map(function (job) {
          return job.id === update.id ? update : job;
        });
      });

      if (update.status === 'CANCELLED') {
        notify('Download cancelled');
        return true;
      }

      if (isActiveJob(update)) {
        cancelRequestedIds.current.delete(id);
      } else if (update.status === 'COMPLETED') {
        saveCompletedJob(update);
      }
      notify('This download is already finishing and could not be cancelled', 'error');
      return false;
    } catch (error) {
      cancelRequestedIds.current.delete(id);
      notify(error.message || 'Could not cancel download', 'error');
      return false;
    } finally {
      setCancelingIds(function (current) {
        return current.filter(function (cancelingId) {
          return cancelingId !== id;
        });
      });
    }
  }

  function clearFinished() {
    const kept = jobs.filter(isActiveJob);
    commit(function () {
      return kept;
    });
    return kept.length;
  }

  return {
    jobs: jobs,
    starting: starting,
    activeCount: activeJobs.length,
    start: start,
    cancel: cancel,
    cancelingIds: cancelingIds,
    clearFinished: clearFinished
  };
}
