export const ACTIVE_STATUSES = ['QUEUED', 'DISCOVERING', 'DOWNLOADING', 'VERIFYING'];
export const isActiveJob = (job) => ACTIVE_STATUSES.includes(job.status);
