import {
  FileArchive, FileAudio2, FileCode2, FileImage, FileText, HardDrive,
} from 'lucide-react';

const ICON_BY_EXTENSION = [
  [['zip', 'rar', '7z', 'tar', 'gz'], FileArchive],
  [['mp3', 'wav', 'flac', 'm4a'], FileAudio2],
  [['png', 'jpg', 'jpeg', 'gif', 'webp'], FileImage],
  [['js', 'java', 'json', 'py', 'ts', 'html', 'css'], FileCode2],
  [['txt', 'md', 'pdf', 'doc', 'docx'], FileText],
];

export function fileIcon(name = '') {
  const parts = name.split('.');
  const ext = parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
  const match = ICON_BY_EXTENSION.find(([exts]) => exts.includes(ext));
  return match ? match[1] : HardDrive;
}
