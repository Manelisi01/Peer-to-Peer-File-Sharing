import { CircleAlert, Network, Search } from 'lucide-react';

export default function PeerResults({ peers, searchDone }) {
  let body;
  if (!searchDone) {
    body = <div className="peer-placeholder"><span className="placeholder-ring"><Search size={17} /></span><span>Enter a file hash to discover active seeders.</span></div>;
  } else if (peers.length === 0) {
    body = <div className="peer-placeholder no-peer"><span className="placeholder-ring"><CircleAlert size={17} /></span><span>No active seeder found for this hash.</span></div>;
  } else {
    body = peers.map((peer) => (
      <div className="peer-row" key={`${peer.ip}:${peer.port}`}>
        <span className="peer-avatar"><Network size={15} /></span>
        <span className="peer-address"><strong>{peer.ip}</strong><small>TCP peer · port {peer.port}</small></span>
        <span className="peer-online"><i /> ONLINE</span>
      </div>
    ));
  }

  return (
    <div className="peer-results">
      <div className="results-heading"><span>AVAILABLE PEERS</span><span>{searchDone ? `${peers.length} FOUND` : 'LOOKUP REQUIRED'}</span></div>
      {body}
    </div>
  );
}
