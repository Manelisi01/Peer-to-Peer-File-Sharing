'use strict';
// Runs in the window before the page. It exposes exactly one capability to the web UI:
// turning a File chosen in the picker (or dropped on the page) into its path on disk, so the
// backend can share the file where it is instead of receiving a copy.
const { contextBridge, webUtils } = require('electron');

contextBridge.exposeInMainWorld('p2pDesktop', {
  getPathForFile(file) {
    try {
      return webUtils.getPathForFile(file) || '';
    } catch {
      return '';
    }
  },
});
