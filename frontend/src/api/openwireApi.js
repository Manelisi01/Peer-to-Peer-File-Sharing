import { request, jsonPost } from './httpClient';

export const openwireApi = {
  getStatus: function () {
    return request('/api/status');
  },

  getSeeders: function () {
    return request('/api/seeders');
  },

  getNetworkInfo: function () {
    return request('/api/network-info');
  },

  getTrackerStatus: function (net) {
    return request('/api/tracker/status?trackerHost=' + encodeURIComponent(net.trackerHost)
      + '&trackerPort=' + encodeURIComponent(net.trackerPort));
  },

  uploadFile: function (file) {
    const form = new FormData();
    form.append('file', file);
    return request('/api/files', { method: 'POST', body: form });
  },

  hashLocalFile: function (path) {
    return jsonPost('/api/files/local', { path: path });
  },

  startSeedingLocal: function (path, net) {
    return jsonPost('/api/seeders/local', {
      path: path,
      peerIp: net.peerIp,
      peerPort: Number(net.peerPort),
      trackerHost: net.trackerHost,
      trackerPort: Number(net.trackerPort)
    });
  },

  stopSharing: function (hash) {
    return request('/api/seeders?hash=' + encodeURIComponent(hash), { method: 'DELETE' });
  },

  deleteFile: function (fileName) {
    return request('/api/files?fileName=' + encodeURIComponent(fileName), { method: 'DELETE' });
  },

  startSeeding: function (fileName, net) {
    return jsonPost('/api/seeders', {
      fileName: fileName,
      peerIp: net.peerIp,
      peerPort: Number(net.peerPort),
      trackerHost: net.trackerHost,
      trackerPort: Number(net.trackerPort)
    });
  },

  findPeers: function (hash, net) {
    return request('/api/peers?hash=' + encodeURIComponent(hash) + '&trackerHost=' + encodeURIComponent(net.trackerHost) + '&trackerPort=' + encodeURIComponent(net.trackerPort));
  },

  startDownload: function (info, net) {
    return jsonPost('/api/downloads', {
      targetFile: info.targetFile,
      fileHash: info.fileHash,
      trackerHost: net.trackerHost,
      trackerPort: Number(net.trackerPort),
      peerIp: net.peerIp,
      peerPort: Number(net.peerPort)
    });
  },

  getDownload: function (id) {
    return request('/api/downloads/' + id);
  },

  cancelDownload: function (id) {
    return request('/api/downloads/' + id + '/cancel', { method: 'POST' });
  }
};
