import { FileArchive, HardDrive, ShieldCheck } from 'lucide-react';
import DropZone from './DropZone';
import FileRow from './FileRow';

export default function LibraryPanel({
  files, busy, onUpload, onSeed, onUseHash, onDelete, onCopyHash,
}) {
  return (
    <section className="panel library-panel">
      <div className="section-heading">
        <div><p className="eyebrow">YOUR NODE</p><h2>Shared library <span className="count-badge">{files.length}</span></h2></div>
        <span className="heading-icon"><HardDrive size={17} /></span>
      </div>

      <DropZone uploading={busy === 'upload'} onFiles={onUpload} />

      <div className="file-list-heading"><span>FILE</span><span>SIZE</span><span>STATE</span></div>
      <div className="file-list">
        {files.length === 0 ? (
          <div className="empty-files"><span className="empty-mark"><FileArchive size={20} /></span><strong>No files in this node</strong><small>Add a file to start sharing with peers.</small></div>
        ) : files.map((file) => (
          <FileRow
            key={`${file.fileHash}-${file.fileName}`}
            file={file}
            busy={busy}
            onSeed={onSeed}
            onUseHash={onUseHash}
            onDelete={onDelete}
            onCopyHash={onCopyHash}
          />
        ))}
      </div>
      <div className="library-footer"><ShieldCheck size={14} /><span>File integrity checked with SHA-256</span></div>
    </section>
  );
}
