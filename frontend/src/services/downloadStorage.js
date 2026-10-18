// Persistence boundary: swap localStorage for anything else here only.
const KEY = 'openwire-downloads';

export const downloadStorage = {
  load() {
    try {
      return JSON.parse(localStorage.getItem(KEY) || '[]');
    } catch {
      return [];
    }
  },
  save(jobs) {
    try {
      localStorage.setItem(KEY, JSON.stringify(jobs));
    } catch {
      /* storage unavailable; keep working in memory */
    }
  },
};
