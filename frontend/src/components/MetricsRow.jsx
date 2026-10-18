import { Activity, ArrowDownToLine, Share2 } from 'lucide-react';

export default function MetricsRow({ online, sharedFiles, activeTransfers }) {
  return (
    <section className="metrics-row" aria-label="Network summary">
      <div className="metric"><span className="metric-icon green"><Activity size={16} /></span><div><span className="metric-label">TRACKER</span><strong>{online ? 'Connected' : 'Offline'}</strong></div></div>
      <div className="metric"><span className="metric-icon orange"><Share2 size={16} /></span><div><span className="metric-label">SHARED FILES</span><strong>{sharedFiles ?? '--'}</strong></div></div>
      <div className="metric"><span className="metric-icon blue"><ArrowDownToLine size={16} /></span><div><span className="metric-label">ACTIVE TRANSFERS</span><strong>{activeTransfers}</strong></div></div>
      <div className="metric-protocol"><span className="protocol-dot" /> SHA-256 VERIFIED <span className="protocol-separator">/</span> TCP CHUNKS</div>
    </section>
  );
}
