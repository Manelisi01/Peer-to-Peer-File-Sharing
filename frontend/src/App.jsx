import { startTransition, useEffect, useState } from 'react';
import {
  Activity,
  ArrowDownToLine,
  ArrowUpFromLine,
  Check,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  CircleHelp,
  Copy,
  FileArchive,
  FileAudio2,
  FileCode2,
  FileImage,
  FileText,
  HardDrive,
  LoaderCircle,
  Network,
  Plus,
  Radio,
  RefreshCw,
  Search,
  Server,
  Share2,
  ShieldCheck,
  Trash2,
  Upload,
  X,
} from 'lucide-react';

const initialNetwork = {
  trackerHost: '127.0.0.1',
  trackerPort: 5000,
  peerIp: '127.0.0.1',
  peerPort: 7000,
};

async function request(path, options = {}) {
  const response = await fetch(path, options);
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json')
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message = typeof body === 'string' ? body : body.message || body.error;
    throw new Error(message || `Request failed (${response.status})`);
  }
  return body;
}

function formatSize(bytes = 0) {
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

function shortHash(hash = '') {
  if (hash.length < 20) return hash;
  return `${hash.slice(0, 10)}...${hash.slice(-8)}`;
}

function fileIcon(name = '') {
  const extension = name.split('.').pop()?.toLowerCase();
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(extension)) return FileArchive;
  if (['mp3', 'wav', 'flac', 'm4a'].includes(extension)) return FileAudio2;
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(extension)) return FileImage;
  if (['js', 'java', 'json', 'py', 'ts', 'html', 'css'].includes(extension)) return FileCode2;
  if (['txt', 'md', 'pdf', 'doc', 'docx'].includes(extension)) return FileText;
  return HardDrive;
}

function StatusPill({ status }) {
  const normalized = (status || 'QUEUED').toLowerCase();
  return <span className={`status-pill status-${normalized}`}>{status || 'QUEUED'}</span>;
}

function App() {
  const [network, setNetwork] = useState(initialNetwork);
  const [server, setServer] = useState(null);
  const [files, setFiles] = useState([]);
  const [jobs, setJobs] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('openwire-downloads') || '[]');
    } catch {
      return [];
    }
  });
  const [hashInput, setHashInput] = useState('');
  const [targetFile, setTargetFile] = useState('');
  const [peers, setPeers] = useState([]);
  const [peerSearchDone, setPeerSearchDone] = useState(false);
  const [busy, setBusy] = useState('');
  const [dragging, setDragging] = useState(false);
  const [toast, setToast] = useState(null);
  const [showNetwork, setShowNetwork] = useState(false);

  const activeJobIds = jobs
    .filter((job) => ['QUEUED', 'DISCOVERING', 'DOWNLOADING', 'VERIFYING'].includes(job.status))
    .map((job) => job.id)
    .join('|');

  useEffect(() => {
    const loadDashboard = async () => {
      try {
        const [status, activeFiles] = await Promise.all([
          request('/api/status'),
          request('/api/seeders'),
        ]);
        setServer(status);
        setFiles((current) => {
          const seededNames = new Set(activeFiles.map((file) => file.fileName));
          const waitingToSeed = current.filter((file) => !file.seeded && !seededNames.has(file.fileName));
          return [...activeFiles.map((file) => ({ ...file, seeded: true })), ...waitingToSeed];
        });
      } catch {
        setServer({ status: 'DOWN', sharedFiles: 0 });
      }
    };

    loadDashboard();
    const interval = window.setInterval(loadDashboard, 12000);
    return () => window.clearInterval(interval);
  }, []);

  useEffect(() => {
    if (!activeJobIds) return undefined;
    const ids = activeJobIds.split('|');
    const poll = async () => {
      const updates = await Promise.all(ids.map(async (id) => {
        try {
          return await request(`/api/downloads/${id}`);
        } catch {
          return null;
        }
      }));
      setJobs((current) => {
        const updated = current.map((job) => updates.find((item) => item?.id === job.id) || job);
        localStorage.setItem('openwire-downloads', JSON.stringify(updated));
        return updated;
      });
    };
    poll();
    const interval = window.setInterval(poll, 1400);
    return () => window.clearInterval(interval);
  }, [activeJobIds]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), 3600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  function notify(message, tone = 'success') {
    setToast({ message, tone });
  }

  async function refreshFiles() {
    try {
      const [status, activeFiles] = await Promise.all([
        request('/api/status'),
        request('/api/seeders'),
      ]);
      setServer(status);
      setFiles((current) => {
        const uploaded = current.filter((file) => !file.seeded);
        return [...activeFiles.map((file) => ({ ...file, seeded: true })), ...uploaded];
      });
    } catch {
      setServer({ status: 'DOWN', sharedFiles: 0 });
    }
  }

  async function uploadFiles(fileList) {
    const selected = Array.from(fileList || []);
    if (!selected.length) return;
    setBusy('upload');
    try {
      const results = await Promise.allSettled(selected.map(async (file) => {
        const form = new FormData();
        form.append('file', file);
        return request('/api/files', { method: 'POST', body: form });
      }));
      const uploaded = results.filter((result) => result.status === 'fulfilled').map((result) => result.value);
      if (uploaded.length === 0) {
        throw results.find((result) => result.status === 'rejected')?.reason || new Error('Upload failed');
      }
      setFiles((current) => [
        ...uploaded.map((file) => ({ ...file, seeded: false, peerIp: network.peerIp, peerPort: network.peerPort })),
        ...current,
      ]);
      if (uploaded.length === 1) setHashInput(uploaded[0].fileHash);
      const failures = results.length - uploaded.length;
      notify(failures
        ? `${uploaded.length} file${uploaded.length === 1 ? '' : 's'} added; ${failures} failed`
        : `${uploaded.length} file${uploaded.length === 1 ? '' : 's'} added to storage`, failures ? 'error' : 'success');
    } catch (error) {
      notify(error.message || 'Upload failed', 'error');
    } finally {
      setBusy('');
    }
  }

  async function startSeeding(file) {
    setBusy(`seed-${file.fileName}`);
    try {
      await request('/api/seeders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fileName: file.fileName,
          peerIp: network.peerIp,
          peerPort: Number(network.peerPort),
          trackerHost: network.trackerHost,
          trackerPort: Number(network.trackerPort),
        }),
      });
      await refreshFiles();
      notify(`${file.fileName} is now being shared`);
    } catch (error) {
      notify(error.message || 'Could not start seeding', 'error');
    } finally {
      setBusy('');
    }
  }

  async function deleteFile(file) {
    const confirmed = window.confirm(
      `Delete ${file.fileName} from local storage and stop seeding it?`,
    );
    if (!confirmed) return;

    setBusy(`delete-${file.fileName}`);
    try {
      await request(`/api/files?fileName=${encodeURIComponent(file.fileName)}`, {
        method: 'DELETE',
      });
      setFiles((current) => current.filter((item) => item.fileName !== file.fileName));
      if (hashInput === file.fileHash) {
        setPeers([]);
        setPeerSearchDone(false);
      }
      await refreshFiles();
      notify(`${file.fileName} deleted from this node`);
    } catch (error) {
      notify(error.message || 'Could not delete this file', 'error');
    } finally {
      setBusy('');
    }
  }

  function clearHistory() {
    const activeStatuses = ['QUEUED', 'DISCOVERING', 'DOWNLOADING', 'VERIFYING'];
    const activeJobs = jobs.filter((job) => activeStatuses.includes(job.status));
    setJobs(activeJobs);
    localStorage.setItem('openwire-downloads', JSON.stringify(activeJobs));
    notify(activeJobs.length
      ? `History cleared; ${activeJobs.length} active transfer${activeJobs.length === 1 ? '' : 's'} kept`
      : 'Download history cleared');
  }

  async function searchPeers(event) {
    event?.preventDefault();
    const hash = hashInput.trim();
    if (!/^[\da-f]{64}$/i.test(hash)) {
      notify('Enter a 64-character SHA-256 hash', 'error');
      return;
    }
    setBusy('peers');
    try {
      const found = await request(`/api/peers?hash=${encodeURIComponent(hash)}`);
      startTransition(() => setPeers(found));
      setPeerSearchDone(true);
      notify(`${found.length} peer${found.length === 1 ? '' : 's'} found`);
    } catch (error) {
      notify(error.message || 'Peer lookup failed', 'error');
    } finally {
      setBusy('');
    }
  }

  async function startDownload(event) {
    event.preventDefault();
    const hash = hashInput.trim();
    if (!/^[\da-f]{64}$/i.test(hash)) {
      notify('Enter a valid SHA-256 hash before downloading', 'error');
      return;
    }
    if (!targetFile.trim()) {
      notify('Choose a name for the downloaded file', 'error');
      return;
    }
    setBusy('download');
    try {
      const job = await request('/api/downloads', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          targetFile: targetFile.trim(),
          fileHash: hash,
          trackerHost: network.trackerHost,
          trackerPort: Number(network.trackerPort),
          peerIp: network.peerIp,
          peerPort: Number(network.peerPort),
        }),
      });
      setJobs((current) => {
        const updated = [job, ...current.filter((item) => item.id !== job.id)];
        localStorage.setItem('openwire-downloads', JSON.stringify(updated));
        return updated;
      });
      setTargetFile('');
      notify('Download queued');
    } catch (error) {
      notify(error.message || 'Could not start download', 'error');
    } finally {
      setBusy('');
    }
  }

  function useFileHash(file) {
    setHashInput(file.fileHash);
    setPeerSearchDone(false);
    setPeers([]);
    notify('File hash ready for peer lookup');
  }

  async function copyHash(hash) {
    try {
      await navigator.clipboard.writeText(hash);
      notify('SHA-256 copied');
    } catch {
      notify('Clipboard access is unavailable', 'error');
    }
  }

  const online = server?.status === 'UP';
  const activeDownloads = jobs.filter((job) =>
    ['QUEUED', 'DISCOVERING', 'DOWNLOADING', 'VERIFYING'].includes(job.status),
  ).length;
  const transferHistory = jobs.slice(0, 6);

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="Openwire home">
          <span className="brand-mark"><Network size={20} strokeWidth={2.2} /></span>
          <span className="brand-name">openwire<span className="brand-period">.</span></span>
          <span className="brand-divider" />
          <span className="brand-caption">P2P CONTROL ROOM</span>
        </a>
        <div className="topbar-right">
          <button className="icon-button refresh-button" onClick={refreshFiles} title="Refresh status" aria-label="Refresh status">
            <RefreshCw size={16} />
          </button>
          <div className={`connection-indicator ${online ? 'is-online' : 'is-offline'}`}>
            <span className="connection-dot" />
            <span>{online ? 'Network online' : 'API unavailable'}</span>
          </div>
          <button className="network-trigger" onClick={() => setShowNetwork((value) => !value)}>
            <span className="network-trigger-icon"><Server size={15} /></span>
            <span>Node settings</span>
            <ChevronDown size={14} className={showNetwork ? 'chevron-up' : ''} />
          </button>
        </div>
      </header>

      {showNetwork && (
        <section className="network-drawer" aria-label="Network settings">
          <div className="drawer-heading">
            <div><span className="eyebrow">CONNECTION</span><h2>Node settings</h2></div>
            <button className="icon-button" onClick={() => setShowNetwork(false)} title="Close settings" aria-label="Close settings"><X size={17} /></button>
          </div>
          <div className="network-fields">
            <label>Tracker host<input value={network.trackerHost} onChange={(event) => setNetwork({ ...network, trackerHost: event.target.value })} /></label>
            <label>Tracker UDP port<input type="number" min="1" max="65535" value={network.trackerPort} onChange={(event) => setNetwork({ ...network, trackerPort: event.target.value })} /></label>
            <label>Advertised peer IP<input value={network.peerIp} onChange={(event) => setNetwork({ ...network, peerIp: event.target.value })} /></label>
            <label>Peer TCP port<input type="number" min="1" max="65535" value={network.peerPort} onChange={(event) => setNetwork({ ...network, peerPort: event.target.value })} /></label>
          </div>
          <p className="drawer-note"><CircleHelp size={14} /> The peer address and port must be reachable by other nodes.</p>
        </section>
      )}

      <main id="top" className="workspace">
        <section className="page-intro">
          <div>
            <p className="eyebrow">PEER-TO-PEER / NODE 01</p>
            <h1>File exchange, <span>in motion.</span></h1>
            <p className="intro-copy">Share directly. Find a peer. Keep every transfer verifiable.</p>
          </div>
          <div className="intro-signal" aria-hidden="true">
            <div className="signal-label"><span>LOCAL NODE</span><span>01 / ACTIVE</span></div>
            <div className="signal-lines"><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /></div>
            <div className="signal-footer"><span>UDP : {network.trackerPort}</span><span>TCP : {network.peerPort}</span></div>
          </div>
        </section>

        <section className="metrics-row" aria-label="Network summary">
          <div className="metric"><span className="metric-icon green"><Activity size={16} /></span><div><span className="metric-label">TRACKER</span><strong>{online ? 'Connected' : 'Offline'}</strong></div></div>
          <div className="metric"><span className="metric-icon orange"><Share2 size={16} /></span><div><span className="metric-label">SHARED FILES</span><strong>{server?.sharedFiles ?? '--'}</strong></div></div>
          <div className="metric"><span className="metric-icon blue"><ArrowDownToLine size={16} /></span><div><span className="metric-label">ACTIVE TRANSFERS</span><strong>{activeDownloads}</strong></div></div>
          <div className="metric-protocol"><span className="protocol-dot" /> SHA-256 VERIFIED <span className="protocol-separator">/</span> TCP CHUNKS
          </div>
        </section>

        <div className="content-grid">
          <section className="panel library-panel">
            <div className="section-heading">
              <div><p className="eyebrow">YOUR NODE</p><h2>Shared library <span className="count-badge">{files.length}</span></h2></div>
              <span className="heading-icon"><HardDrive size={17} /></span>
            </div>

            <label
              className={`dropzone ${dragging ? 'is-dragging' : ''} ${busy === 'upload' ? 'is-busy' : ''}`}
              onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
              onDragLeave={() => setDragging(false)}
              onDrop={(event) => { event.preventDefault(); setDragging(false); uploadFiles(event.dataTransfer.files); }}
            >
              <input type="file" multiple onChange={(event) => { uploadFiles(event.target.files); event.target.value = ''; }} />
              <span className="drop-icon">{busy === 'upload' ? <LoaderCircle className="spin" size={21} /> : <Upload size={21} />}</span>
              <span className="drop-copy"><strong>{busy === 'upload' ? 'Adding files...' : 'Drop files to add'}</strong><small>Stored locally, then ready to seed</small></span>
              <span className="drop-action"><Plus size={16} /> Browse</span>
            </label>

            <div className="file-list-heading"><span>FILE</span><span>SIZE</span><span>STATE</span></div>
            <div className="file-list">
              {files.length === 0 ? (
                <div className="empty-files"><span className="empty-mark"><FileArchive size={20} /></span><strong>No files in this node</strong><small>Add a file to start sharing with peers.</small></div>
              ) : files.map((file) => {
                const Icon = fileIcon(file.fileName);
                return (
                  <article className="file-row" key={`${file.fileHash}-${file.fileName}`}>
                    <span className={`file-type-icon ${file.seeded ? 'seeded' : ''}`}><Icon size={17} /></span>
                    <div className="file-main"><strong title={file.fileName}>{file.fileName}</strong><button className="hash-copy" onClick={() => copyHash(file.fileHash)} title="Copy SHA-256"><span>{shortHash(file.fileHash)}</span><Copy size={12} /></button></div>
                    <span className="file-size">{formatSize(file.fileSize)}</span>
                    <div className="file-actions">
                      {file.seeded ? <span className="seed-state"><span /> SEEDING</span> : <button className="seed-button" onClick={() => startSeeding(file)} disabled={busy === `seed-${file.fileName}`}>{busy === `seed-${file.fileName}` ? <LoaderCircle className="spin" size={13} /> : <Radio size={13} />} Seed</button>}
                      <button className="row-more" onClick={() => useFileHash(file)} title="Use hash for peer lookup" aria-label={`Use ${file.fileName} hash`}><Search size={15} /></button>
                      <button className="row-delete" onClick={() => deleteFile(file)} disabled={busy === `delete-${file.fileName}`} title={`Delete ${file.fileName} from storage`} aria-label={`Delete ${file.fileName}`}>
                        {busy === `delete-${file.fileName}` ? <LoaderCircle className="spin" size={14} /> : <Trash2 size={14} />}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
            <div className="library-footer"><ShieldCheck size={14} /><span>File integrity checked with SHA-256</span></div>
          </section>

          <section className="panel transfer-panel">
            <div className="section-heading transfer-heading">
              <div><p className="eyebrow">NETWORK EXCHANGE</p><h2>Find &amp; receive</h2></div>
              <span className="heading-icon"><Network size={17} /></span>
            </div>

            <form className="peer-search-form" onSubmit={searchPeers}>
              <label htmlFor="hash-input">FILE SHA-256</label>
              <div className="input-with-button">
                <input id="hash-input" value={hashInput} onChange={(event) => { setHashInput(event.target.value); setPeerSearchDone(false); }} placeholder="Paste a 64-character hash" spellCheck="false" />
                <button type="submit" className="search-button" disabled={busy === 'peers'}>{busy === 'peers' ? <LoaderCircle className="spin" size={16} /> : <Search size={16} />}<span>Find peers</span></button>
              </div>
            </form>

            <div className="peer-results">
              <div className="results-heading"><span>AVAILABLE PEERS</span><span>{peerSearchDone ? `${peers.length} FOUND` : 'LOOKUP REQUIRED'}</span></div>
              {!peerSearchDone ? (
                <div className="peer-placeholder"><span className="placeholder-ring"><Search size={17} /></span><span>Enter a file hash to discover active seeders.</span></div>
              ) : peers.length === 0 ? (
                <div className="peer-placeholder no-peer"><span className="placeholder-ring"><CircleAlert size={17} /></span><span>No active seeder found for this hash.</span></div>
              ) : peers.map((peer) => (
                <div className="peer-row" key={`${peer.ip}:${peer.port}`}><span className="peer-avatar"><Network size={15} /></span><span className="peer-address"><strong>{peer.ip}</strong><small>TCP peer · port {peer.port}</small></span><span className="peer-online"><i /> ONLINE</span></div>
              ))}
            </div>

            <form className="download-form" onSubmit={startDownload}>
              <div className="download-form-title"><span className="download-icon"><ArrowDownToLine size={16} /></span><div><strong>Receive this file</strong><small>Verified before it is saved</small></div></div>
              <label htmlFor="target-file">SAVE AS</label>
              <div className="input-with-button">
                <input id="target-file" value={targetFile} onChange={(event) => setTargetFile(event.target.value)} placeholder="filename.ext" />
                <button type="submit" className="download-button" disabled={busy === 'download'}>{busy === 'download' ? <LoaderCircle className="spin" size={16} /> : <ArrowDownToLine size={16} />}<span>Download</span></button>
              </div>
              <p className="form-footnote"><HardDrive size={13} /> Saves to the server's configured storage directory</p>
            </form>
          </section>
        </div>

        <section className="panel activity-panel">
          <div className="section-heading activity-heading">
            <div><p className="eyebrow">LIVE QUEUE</p><h2>Transfer activity</h2></div>
            <div className="activity-actions">
              {jobs.length > 0 && <button className="history-clear" onClick={clearHistory} title="Clear completed and failed transfers">
                <Trash2 size={13} /><span>Clear history</span>
              </button>}
              <span className="activity-count">{jobs.length} JOB{jobs.length === 1 ? '' : 'S'}</span>
            </div>
          </div>
          {transferHistory.length === 0 ? (
            <div className="empty-activity"><span className="activity-line" /><span>No transfers yet. Downloads will appear here with live progress.</span></div>
          ) : (
            <div className="job-list">
              {transferHistory.map((job) => {
                const complete = job.status === 'COMPLETED';
                const failed = job.status === 'FAILED';
                return (
                  <article className="job-row" key={job.id}>
                    <span className={`job-icon ${complete ? 'complete' : failed ? 'failed' : ''}`}>{complete ? <CheckCircle2 size={17} /> : failed ? <CircleAlert size={17} /> : <ArrowDownToLine size={17} />}</span>
                    <div className="job-file"><strong>{job.targetFile}</strong><small>{job.message}</small></div>
                    <div className="job-progress"><div className="progress-track"><span className={failed ? 'progress-failed' : complete ? 'progress-complete' : ''} style={{ width: `${job.progress || (job.status === 'VERIFYING' ? 95 : 0)}%` }} /></div><small>{job.progress ?? 0}%</small></div>
                    <StatusPill status={job.status} />
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <footer className="page-footer"><span><span className="footer-mark">O</span> OPENWIRE <span className="footer-muted">/ PRIVATE PEER NETWORK</span></span><span>UDP TRACKER <i /> TCP CHUNKS <i /> SHA-256</span></footer>
      </main>

      {toast && <div className={`toast toast-${toast.tone}`} role="status">{toast.tone === 'error' ? <CircleAlert size={16} /> : <Check size={16} />}{toast.message}<button onClick={() => setToast(null)} aria-label="Dismiss notification"><X size={14} /></button></div>}
    </div>
  );
}

export default App;