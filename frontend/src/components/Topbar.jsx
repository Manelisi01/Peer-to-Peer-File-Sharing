import { ChevronDown, Network, RefreshCw, Server } from 'lucide-react';

export default function Topbar({ online, settingsOpen, onRefresh, onToggleSettings }) {
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
        <div className={`connection-indicator ${online ? 'is-online' : 'is-offline'}`}>
          <span className="connection-dot" />
          <span>{online ? 'Network online' : 'API unavailable'}</span>
        </div>
        <button className="network-trigger" onClick={onToggleSettings}>
          <span className="network-trigger-icon"><Server size={15} /></span>
          <span>Node settings</span>
          <ChevronDown size={14} className={settingsOpen ? 'chevron-up' : ''} />
        </button>
      </div>
    </header>
  );
}
