// The only module that knows endpoint URLs and payload shapes.
import { request, jsonPost } from './httpClient';

export const openwireApi = {
  getStatus: () => request('/api/status'),
  getSeeders: () => request('/api/seeders'),

  uploadFile(file) {
    const form = new FormData();
    form.append('file', file);
    return request('/api/files', { method: 'POST', body: form });
  },

  // Desktop app: files are hashed and shared where they are, without being copied.
  hashLocalFile: (path) => jsonPost('/api/files/local', { path }),

  startSeedingLocal: (path, net) =>
    jsonPost('/api/seeders/local', {
      path,
      peerIp: net.peerIp,
      peerPort: Number(net.peerPort),
      trackerHost: net.trackerHost,
      trackerPort: Number(net.trackerPort),
    }),

  // Stops sharing only; the file itself is never touched.
  stopSharing: (hash) =>
    request(`/api/seeders?hash=${encodeURIComponent(hash)}`, { method: 'DELETE' }),

  deleteFile: (fileName) =>
    request(`/api/files?fileName=${encodeURIComponent(fileName)}`, { method: 'DELETE' }),

  startSeeding: (fileName, net) =>
    jsonPost('/api/seeders', {
      fileName,
      peerIp: net.peerIp,
      peerPort: Number(net.peerPort),
      trackerHost: net.trackerHost,
      trackerPort: Number(net.trackerPort),
    }),

  findPeers: (hash) => request(`/api/peers?hash=${encodeURIComponent(hash)}`),

  startDownload: ({ targetFile, fileHash }, net) =>
    jsonPost('/api/downloads', {
      targetFile,
      fileHash,
      trackerHost: net.trackerHost,
      trackerPort: Number(net.trackerPort),
      peerIp: net.peerIp,
      peerPort: Number(net.peerPort),
    }),

  getDownload: (id) => request(`/api/downloads/${id}`),

  cancelDownload: (id) => request(`/api/downloads/${id}/cancel`, { method: 'POST' }),
};
