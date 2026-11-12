import { ChevronDown, Network, RefreshCw, Server } from 'lucide-react';

export default function Topbar(props) {
  const { online, settingsOpen, onRefresh, onToggleSettings } = props;

  let connectionClass = 'connection-indicator';
  if (online) {
    connectionClass += ' is-online';
  } else {
    connectionClass += ' is-offline';
  }

  let chevronClass = '';
  if (settingsOpen) {
    chevronClass = 'chevron-up';
  }

  return (
    <header className="topbar">
      <a className="brand" href="#top" aria-label="Openwire home">
        <span className="brand-mark"><Network size={20} strokeWidth={2.2} /></span>
        <span className="brand-name">openwire<span className="brand-period">.</span></span>
        <span className="brand-divider" />
        <span className="brand-caption">P2P CONTROL ROOM</span>
      </a>
      <div className="topbar-right">
        <button className="icon-button refresh-button" onClick={onRefresh} title="Refresh status" aria-label="Refresh status">
          <RefreshCw size={16} />
        </button>
        <div className={connectionClass}>
          <span className="connection-dot" />
          <span>{online ? 'Online' : 'Offline'}</span>
        </div>
        <button className="network-trigger" onClick={onToggleSettings}>
          <span className="network-trigger-icon"><Server size={15} /></span>
          <span>Node settings</span>
          <ChevronDown size={14} className={chevronClass} />
        </button>
      </div>
    </header>
  );
}
