export const ACTIVE_STATUSES = ['QUEUED', 'DISCOVERING', 'DOWNLOADING', 'VERIFYING'];

export function isActiveJob(job) {
  return ACTIVE_STATUSES.indexOf(job.status) !== -1;
}
