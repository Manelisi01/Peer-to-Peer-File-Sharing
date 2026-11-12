import { useState } from 'react';
import { LoaderCircle, Plus, Upload } from 'lucide-react';
import { isDesktop } from '../services/desktop';

export default function DropZone(props) {
  const { uploading, onFiles } = props;
  const [dragging, setDragging] = useState(false);

  let zoneClass = 'dropzone';
  if (dragging) {
    zoneClass += ' is-dragging';
  }
  if (uploading) {
    zoneClass += ' is-busy';
  }

  return (
    <label
      className={zoneClass}
      onDragOver={function (event) {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={function () {
        setDragging(false);
      }}
      onDrop={function (event) {
        event.preventDefault();
        setDragging(false);
        onFiles(event.dataTransfer.files);
      }}
    >
      <input
        type="file"
        multiple
        onChange={function (event) {
          onFiles(event.target.files);
          event.target.value = '';
        }}
      />
      <span className="drop-icon">{uploading ? <LoaderCircle className="spin" size={21} /> : <Upload size={21} />}</span>
      <span className="drop-copy"><strong>{uploading ? 'Adding files...' : 'Drop files to add'}</strong><small>{isDesktop ? 'Shared from where it is - no copy is made' : 'Stored locally, then ready to seed'}</small></span>
      <span className="drop-action"><Plus size={16} /> Browse</span>
    </label>
  );
}
