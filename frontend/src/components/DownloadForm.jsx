import { useState } from 'react';
import { ArrowDownToLine, HardDrive, LoaderCircle } from 'lucide-react';

export default function DownloadForm({ submitting, onSubmit }) {
  const [targetFile, setTargetFile] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();
    const queued = await onSubmit(targetFile);
    if (queued) setTargetFile('');
  }

  return (
    <form className="download-form" onSubmit={handleSubmit}>
      <div className="download-form-title"><span className="download-icon"><ArrowDownToLine size={16} /></span><div><strong>Receive this file</strong><small>Verified before it is saved</small></div></div>
      <label htmlFor="target-file">SAVE AS</label>
      <div className="input-with-button">
        <input id="target-file" value={targetFile} onChange={(e) => setTargetFile(e.target.value)} placeholder="filename.ext" />
        <button type="submit" className="download-button" disabled={submitting}>
          {submitting ? <LoaderCircle className="spin" size={16} /> : <ArrowDownToLine size={16} />}<span>Download</span>
        </button>
      </div>
      <p className="form-footnote"><HardDrive size={13} /> Saves to the server's configured storage directory</p>
    </form>
  );
}
