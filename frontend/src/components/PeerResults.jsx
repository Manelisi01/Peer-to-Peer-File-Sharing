import { CircleAlert, Network } from 'lucide-react';

export default function PeerResults(props) {
  const { peers, searchDone } = props;
  let body;

  if (!searchDone) {
    body = <div className="line-break"><br /></div>;
  } else if (peers.length === 0) {
    body = <div className="peer-placeholder no-peer"><span className="placeholder-ring"><CircleAlert size={17} /></span><span>No active seeder found for this hash.</span></div>;
  } else {
    body = peers.map(function (peer) {
      return (
        <div className="peer-row" key={peer.ip + ':' + peer.port}>
          <span className="peer-avatar"><Network size={15} /></span>
          <span className="peer-address"><strong>{peer.ip}</strong><small>TCP peer · port {peer.port}</small></span>
          <span className="peer-online"><i /> ONLINE</span>
        </div>
      );
    });
  }

  let heading = 'LOOKUP REQUIRED';
  if (searchDone) {
    heading = peers.length + ' FOUND';
  }

  return (
    <div className="peer-results">
      <div className="results-heading"><span>AVAILABLE PEERS</span><span>{heading}</span></div>
      {body}
    </div>
  );
}
