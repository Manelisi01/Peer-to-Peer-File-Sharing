import { LoaderCircle, Network, Search } from 'lucide-react';
import PeerResults from './PeerResults';
import DownloadForm from './DownloadForm';

export default function TransferPanel({ peerSearch, downloading, onDownload }) {
  const { hashInput, setHashInput, peers, searchDone, searching, search } = peerSearch;
  return (
    <section className="panel transfer-panel">
      <div className="section-heading transfer-heading">
        <div><p className="eyebrow">NETWORK EXCHANGE</p><h2>Find &amp; receive</h2></div>
        <span className="heading-icon"><Network size={17} /></span>
      </div>

      <form className="peer-search-form" onSubmit={search}>
        <label htmlFor="hash-input">FILE SHA-256</label>
        <div className="input-with-button">
          <input id="hash-input" value={hashInput} onChange={(e) => setHashInput(e.target.value)} placeholder="Paste a 64-character hash" spellCheck="false" />
          <button type="submit" className="search-button" disabled={searching}>
            {searching ? <LoaderCircle className="spin" size={16} /> : <Search size={16} />}<span>Find peers</span>
          </button>
        </div>
      </form>

      <PeerResults peers={peers} searchDone={searchDone} />
      <DownloadForm submitting={downloading} onSubmit={onDownload} />
    </section>
  );
}
