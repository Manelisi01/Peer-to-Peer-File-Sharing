import { useState } from 'react';
import { useToast } from './hooks/useToast';
import { useNetworkSettings } from './hooks/useNetworkSettings';
import { useLibrary } from './hooks/useLibrary';
import { useDownloads } from './hooks/useDownloads';
import { usePeerSearch } from './hooks/usePeerSearch';

import Topbar from './components/Topbar';
import NodeSettingsDrawer from './components/NodeSettingsDrawer';
import PageIntro from './components/PageIntro';
import MetricsRow from './components/MetricsRow';
import LibraryPanel from './components/LibraryPanel';
import TransferPanel from './components/TransferPanel';
import ActivityPanel from './components/ActivityPanel';
import PageFooter from './components/PageFooter';
import Toast from './components/Toast';

// App only wires hooks to components. No fetch calls, no storage, no markup details.
export default function App() {
  const [showSettings, setShowSettings] = useState(false);

  const { toast, notify, dismiss } = useToast();
  const { network, updateField } = useNetworkSettings();
  const library = useLibrary({ network, notify });
  const downloads = useDownloads({ network, notify });
  const peerSearch = usePeerSearch({ notify });

  async function handleUpload(fileList) {
    const uploaded = await library.addFiles(fileList);
    if (uploaded?.length === 1) peerSearch.setHashInput(uploaded[0].fileHash);
  }

  async function handleDelete(file) {
    const deleted = await library.deleteFile(file);
    if (deleted && peerSearch.hashInput === file.fileHash) peerSearch.reset();
  }

  async function copyHash(hash) {
    try {
      await navigator.clipboard.writeText(hash);
      notify('SHA-256 copied');
    } catch {
      notify('Clipboard access is unavailable', 'error');
    }
  }

  function handleClearHistory() {
    const kept = downloads.clearFinished();
    if (kept === 0) return notify('Download history cleared');
    return notify(`History cleared; ${kept} active ${kept === 1 ? 'transfer' : 'transfers'} kept`);
  }

  return (
    <div className="app-shell">
      <Topbar
        online={library.online}
        settingsOpen={showSettings}
        onRefresh={library.refresh}
        onToggleSettings={() => setShowSettings((open) => !open)}
      />

      {showSettings && (
        <NodeSettingsDrawer network={network} onChange={updateField} onClose={() => setShowSettings(false)} />
      )}

      <main id="top" className="workspace">
        <PageIntro trackerPort={network.trackerPort} peerPort={network.peerPort} />
        <MetricsRow
          online={library.online}
          sharedFiles={library.server?.sharedFiles}
          activeTransfers={downloads.activeCount}
        />

        <div className="content-grid">
          <LibraryPanel
            files={library.files}
            busy={library.busy}
            onUpload={handleUpload}
            onSeed={library.startSeeding}
            onUseHash={(file) => peerSearch.useHash(file.fileHash)}
            onDelete={handleDelete}
            onCopyHash={copyHash}
          />
          <TransferPanel
            peerSearch={peerSearch}
            downloading={downloads.starting}
            onDownload={(targetFile) => downloads.start(peerSearch.hashInput, targetFile)}
          />
        </div>

        <ActivityPanel jobs={downloads.jobs} onClear={handleClearHistory} onCancel={downloads.cancel} />
        <PageFooter />
      </main>

      <Toast toast={toast} onDismiss={dismiss} />
    </div>
  );
}
