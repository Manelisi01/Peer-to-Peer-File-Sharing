// Bridge to the desktop shell (see desktop/preload.js).
// In a normal browser window.p2pDesktop does not exist and the app falls back to uploading files.
const bridge = typeof window !== 'undefined' ? window.p2pDesktop : undefined;

export const isDesktop = Boolean(bridge && typeof bridge.getPathForFile === 'function');

// Full path of a File chosen in the file picker or dropped on the page ('' if unavailable).
export function localPathOf(file) {
  if (!isDesktop) return '';
  try {
    return bridge.getPathForFile(file) || '';
  } catch {
    return '';
  }
}
