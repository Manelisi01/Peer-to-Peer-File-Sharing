import { CircleHelp, X } from 'lucide-react';

const FIELDS = [
  { key: 'trackerHost', label: 'Tracker host' },
  { key: 'trackerPort', label: 'Tracker UDP port', port: true },
  { key: 'peerIp', label: 'Advertised peer IP' },
  { key: 'peerPort', label: 'Peer TCP port', port: true },
];

export default function NodeSettingsDrawer({ network, onChange, onClose }) {
  return (
    <section className="network-drawer" aria-label="Network settings">
      <div className="drawer-heading">
        <div><span className="eyebrow">CONNECTION</span><h2>Node settings</h2></div>
        <button className="icon-button" onClick={onClose} title="Close settings" aria-label="Close settings"><X size={17} /></button>
      </div>
      <div className="network-fields">
        {FIELDS.map(({ key, label, port }) => (
          <label key={key}>
            {label}
            <input
              {...(port ? { type: 'number', min: 1, max: 65535 } : {})}
              value={network[key]}
              onChange={(event) => onChange(key, event.target.value)}
            />
          </label>
        ))}
      </div>
      <p className="drawer-note"><CircleHelp size={14} /> Set Tracker host to the Wi-Fi IPv4 address of ONE computer running the tracker, on every PC. Verify Peer IP matches this computer’s Wi-Fi address. Allow inbound UDP 5000 on the tracker and TCP 7000 on seeders.</p>
    </section>
  );
}
