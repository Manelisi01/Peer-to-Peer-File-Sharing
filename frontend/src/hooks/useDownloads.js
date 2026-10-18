import { useEffect, useState } from 'react';
import { openwireApi } from '../api/openwireApi';
import { downloadStorage } from '../services/downloadStorage';
import { isValidSha256 } from '../utils/format';
import { isActiveJob } from '../utils/jobStatus';

export function useDownloads({ network, notify }) {
  const [starting, setStarting] = useState(false);
  const [jobs, setJobs] = useState(() => downloadStorage.load());

  const activeJobs = jobs.filter(isActiveJob);
  const activeJobIds = activeJobs.map((j) => j.id).join('|');

  // Single place that updates state and persists it.
  const commit = (updater) =>
    setJobs((current) => {
      const next = updater(current);
      downloadStorage.save(next);
      return next;
    });

  useEffect(() => {
    if (!activeJobIds) return undefined;
    const ids = activeJobIds.split('|');

    const poll = async () => {
      const updates = await Promise.all(
        ids.map((id) => openwireApi.getDownload(id).catch(() => null)),
      );
      commit((current) =>
        current.map((job) => updates.find((u) => u?.id === job.id) || job),
      );
    };

    poll();
    const interval = window.setInterval(poll, 1400);
    return () => window.clearInterval(interval);
  }, [activeJobIds]);

  const addJob = (job) => commit((current) => [job, ...current.filter((j) => j.id !== job.id)]);

  // Resolves true when the download was queued.
  const start = async (rawHash, rawTarget) => {
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
      addJob(await openwireApi.startDownload({ targetFile, fileHash }, network));
      notify('Download queued');
      return true;
    } catch (error) {
      notify(error.message || 'Could not start download', 'error');
      return false;
    } finally {
      setStarting(false);
    }
  };

  // Returns how many active jobs were kept.
  const clearFinished = () => {
    const kept = jobs.filter(isActiveJob);
    commit(() => kept);
    return kept.length;
  };

  return { jobs, starting, activeCount: activeJobs.length, start, clearFinished };
}
