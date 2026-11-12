export default function StatusPill(props) {
  const { status } = props;
  const displayStatus = status || 'QUEUED';
  return <span className={'status-pill status-' + displayStatus.toLowerCase()}>{displayStatus}</span>;
}
