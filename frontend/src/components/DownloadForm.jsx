import { useState } from 'react';
import { ArrowDownToLine, HardDrive, LoaderCircle } from 'lucide-react';

export default function DownloadForm(props) {
  const { submitting, onSubmit } = props;
  const [targetFile, setTargetFile] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();
    const queued = await onSubmit(targetFile);
    if (queued) {
      setTargetFile('');
    }
  }

  return (
    <form className="download-form" onSubmit={handleSubmit}>
      <div className="download-form-title">
        <span className="download-icon"><ArrowDownToLine size={16} /></span>
        <div><strong>Receive this file</strong><small>Verified before it is saved</small></div>
      </div>

      <label htmlFor="target-file">SAVE AS</label>
      <div className="input-with-button">
        <input
          id="target-file"
          value={targetFile}
          onChange={function (event) {
            setTargetFile(event.target.value);
          }}
          placeholder="filename.pdf, video.mp4, archive.zip"
        />
        <button type="submit" className="download-button" disabled={submitting}>
          {submitting ? <LoaderCircle className="spin" size={16} /> : <ArrowDownToLine size={16} />}<span>Download</span>
        </button>
      </div>
      <p className="form-footnote"><HardDrive size={13} /> Files are saved to Downloads. Include the original extension (e.g. .pdf or .mp4) in the name.</p>
    </form>
  );
}
