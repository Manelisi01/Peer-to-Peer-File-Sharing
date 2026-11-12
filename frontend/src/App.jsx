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

export default function App() {
  const [showSettings, setShowSettings] = useState(false);

  const toastState = useToast();
  const { toast, notify, dismiss } = toastState;

  const networkState = useNetworkSettings();
  const { network, updateField, connected, refreshNetwork } = networkState;

  const library = useLibrary({ network, notify });
  const downloads = useDownloads({ network, notify });
  const peerSearch = usePeerSearch({ notify, network });

  async function handleRefresh() {
    await Promise.all([library.refresh(), refreshNetwork()]);
  }

  async function handleUpload(fileList) {
    const uploaded = await library.addFiles(fileList);
    if (uploaded && uploaded.length === 1) {
      peerSearch.setHashInput(uploaded[0].fileHash);
    }
  }

  async function handleDelete(file) {
    const deleted = await library.deleteFile(file);
    if (deleted && peerSearch.hashInput === file.fileHash) {
      peerSearch.reset();
    }
  }

  async function copyHash(hash) {
    try {
      await navigator.clipboard.writeText(hash);
      notify('SHA-256 copied');
    } catch (error) {
      notify('Clipboard access is unavailable', 'error');
    }
  }

  function handleClearHistory() {
    const kept = downloads.clearFinished();
    if (kept === 0) {
      return notify('Download history cleared');
    }
    return notify('History cleared; ' + kept + ' active ' + (kept === 1 ? 'transfer' : 'transfers') + ' kept');
  }

  const online = connected && library.online;

  return (
    <div className="app-shell">
      <Topbar
        online={online}
        settingsOpen={showSettings}
        onRefresh={handleRefresh}
        onToggleSettings={function () {
          setShowSettings(function (open) {
            return !open;
          });
        }}
      />

      {showSettings && (
        <NodeSettingsDrawer
          network={network}
          onChange={updateField}
          onClose={function () {
            setShowSettings(false);
          }}
        />
      )}

      <main id="top" className="workspace">
        <PageIntro trackerPort={network.trackerPort} peerPort={network.peerPort} />
        <MetricsRow
          online={online}
          sharedFiles={library.server && library.server.sharedFiles}
          activeTransfers={downloads.activeCount}
        />

        <div className="content-grid">
          <LibraryPanel
            files={library.files}
            busy={library.busy}
            onUpload={handleUpload}
            onSeed={library.startSeeding}
            onUseHash={function (file) {
              peerSearch.useHash(file.fileHash);
            }}
            onDelete={handleDelete}
            onCopyHash={copyHash}
          />
          <TransferPanel
            peerSearch={peerSearch}
            downloading={downloads.starting}
            onDownload={function (targetFile) {
              return downloads.start(peerSearch.hashInput, targetFile);
            }}
          />
        </div>

        <ActivityPanel jobs={downloads.jobs} onClear={handleClearHistory} onCancel={downloads.cancel} />
        <PageFooter />
      </main>

      <Toast toast={toast} onDismiss={dismiss} />
    </div>
  );
}
