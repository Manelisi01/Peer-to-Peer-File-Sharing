let bridge;
if (typeof window !== 'undefined') {
  bridge = window.p2pDesktop;
}

export const isDesktop =
  bridge !== undefined && typeof bridge.getPathForFile === 'function';

export function localPathOf(file) {
  if (!isDesktop) {
    return '';
  }

  try {
    return bridge.getPathForFile(file) || '';
  } catch (error) {
    return '';
  }
}

export async function saveCompletedDownload(id, fileName) {
  if (bridge && typeof bridge.saveDownload === 'function') {
    return bridge.saveDownload(id, fileName);
  }

  const anchor = document.createElement('a');
  anchor.href = `/api/downloads/${encodeURIComponent(id)}/file`;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  return 'browser Downloads folder';
}
