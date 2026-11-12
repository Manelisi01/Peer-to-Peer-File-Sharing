import { Copy, LoaderCircle, Radio, Search, Trash2, X } from 'lucide-react';
import { fileIcon } from '../utils/fileIcon';
import { formatSize, shortHash } from '../utils/format';

export default function FileRow(props) {
  const { file, busy, onSeed, onUseHash, onDelete, onCopyHash } = props;

  const Icon = fileIcon(file.fileName);
  const seeding = busy === 'seed-' + file.fileName;
  const deleting = busy === 'delete-' + file.fileName;
  const sharedInPlace = file.managed === false;
  const removeLabel = sharedInPlace ? 'Stop sharing ' + file.fileName + ' (the file itself is kept)' : 'Delete ' + file.fileName + ' from storage';

  return (
    <article className="file-row">
      <span className={'file-type-icon ' + (file.seeded ? 'seeded' : '')}><Icon size={17} /></span>
      <div className="file-main">
        <strong title={file.fileName}>{file.fileName}</strong>
        <button className="hash-copy" onClick={function () { onCopyHash(file.fileHash); }} title="Copy SHA-256">
          <span>{shortHash(file.fileHash)}</span><Copy size={12} />
        </button>
      </div>
      <span className="file-size">{formatSize(file.fileSize)}</span>
      <div className="file-actions">
        {file.seeded ? (
          <span className="seed-state"><span /> SEEDING</span>
        ) : (
          <button className="seed-button" onClick={function () { onSeed(file); }} disabled={seeding}>
            {seeding ? <LoaderCircle className="spin" size={13} /> : <Radio size={13} />} Seed
          </button>
        )}
        <button className="row-more" onClick={function () { onUseHash(file); }} title="Use hash for peer lookup" aria-label={'Use ' + file.fileName + ' hash'}><Search size={15} /></button>
        <button className="row-delete" onClick={function () { onDelete(file); }} disabled={deleting} title={removeLabel} aria-label={removeLabel}>
          {deleting ? <LoaderCircle className="spin" size={14} /> : sharedInPlace ? <X size={14} /> : <Trash2 size={14} />}
        </button>
      </div>
    </article>
  );
}
