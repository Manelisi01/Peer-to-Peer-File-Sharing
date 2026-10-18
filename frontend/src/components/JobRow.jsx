import { ArrowDownToLine, CheckCircle2, CircleAlert } from 'lucide-react';
import StatusPill from './StatusPill';

export default function JobRow({ job }) {
  const complete = job.status === 'COMPLETED';
  const failed = job.status === 'FAILED';
  const iconState = complete ? 'complete' : failed ? 'failed' : '';
  const barState = failed ? 'progress-failed' : complete ? 'progress-complete' : '';
  const width = job.progress || (job.status === 'VERIFYING' ? 95 : 0);

  return (
    <article className="job-row">
      <span className={`job-icon ${iconState}`}>
        {complete ? <CheckCircle2 size={17} /> : failed ? <CircleAlert size={17} /> : <ArrowDownToLine size={17} />}
      </span>
      <div className="job-file"><strong>{job.targetFile}</strong><small>{job.message}</small></div>
      <div className="job-progress">
        <div className="progress-track"><span className={barState} style={{ width: `${width}%` }} /></div>
        <small>{job.progress ?? 0}%</small>
      </div>
      <StatusPill status={job.status} />
    </article>
  );
}
