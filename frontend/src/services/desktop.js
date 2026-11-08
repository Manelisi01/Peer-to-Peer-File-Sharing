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

export async function saveCompletedDownload(id, fileName) {
  if (bridge && typeof bridge.saveDownload === 'function') {
    return bridge.saveDownload(id, fileName);
  }
  // Browser version: use the normal browser download mechanism.
  const anchor = document.createElement('a');
  anchor.href = `/api/downloads/${encodeURIComponent(id)}/file`;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  return 'browser Downloads folder';
}
