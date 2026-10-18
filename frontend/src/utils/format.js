export function formatSize(bytes = 0) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = bytes / 1024;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size.toFixed(size >= 100 ? 0 : 1)} ${units[unit]}`;
}

export const shortHash = (hash = '') =>
  hash.length < 20 ? hash : `${hash.slice(0, 10)}...${hash.slice(-8)}`;

export const isValidSha256 = (value) => /^[\da-f]{64}$/i.test(value);
