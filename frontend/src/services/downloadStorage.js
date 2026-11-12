const KEY = 'openwire-downloads';

export const downloadStorage = {
  load: function () {
    try {
      return JSON.parse(localStorage.getItem(KEY) || '[]');
    } catch (error) {
      return [];
    }
  },

  save: function (jobs) {
    try {
      localStorage.setItem(KEY, JSON.stringify(jobs));
    } catch (error) {
      // storage unavailable; keep working in memory
    }
  }
};
