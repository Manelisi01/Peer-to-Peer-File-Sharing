import { useState } from 'react';
import { LoaderCircle, Plus, Upload } from 'lucide-react';
import { isDesktop } from '../services/desktop';

export default function DropZone({ uploading, onFiles }) {
  const [dragging, setDragging] = useState(false);
  return (
    <label
      className={`dropzone ${dragging ? 'is-dragging' : ''} ${uploading ? 'is-busy' : ''}`}
      onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => { e.preventDefault(); setDragging(false); onFiles(e.dataTransfer.files); }}
    >
      <input type="file" multiple onChange={(e) => { onFiles(e.target.files); e.target.value = ''; }} />
      <span className="drop-icon">{uploading ? <LoaderCircle className="spin" size={21} /> : <Upload size={21} />}</span>
      <span className="drop-copy"><strong>{uploading ? 'Adding files...' : 'Drop files to add'}</strong><small>{isDesktop ? 'Shared from where it is - no copy is made' : 'Stored locally, then ready to seed'}</small></span>
      <span className="drop-action"><Plus size={16} /> Browse</span>
    </label>
  );
}
