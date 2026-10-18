export default function StatusPill({ status }) {
  const displayStatus = status || 'QUEUED';
  return <span className={`status-pill status-${displayStatus.toLowerCase()}`}>{displayStatus}</span>;
}
