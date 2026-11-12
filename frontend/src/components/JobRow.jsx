import { ArrowDownToLine, CheckCircle2, CircleAlert, X } from 'lucide-react';
import StatusPill from './StatusPill';
import { isActiveJob } from '../utils/jobStatus';

export default function JobRow(props) {
  const { job, onCancel, canceling } = props;
  const canCancel = isActiveJob(job) && onCancel;
  const complete = job.status === 'COMPLETED';
  const failed = job.status === 'FAILED';
  const iconState = complete ? 'complete' : failed ? 'failed' : '';
  const barState = failed ? 'progress-failed' : complete ? 'progress-complete' : '';
  let width = job.progress;

  if (width === undefined || width === null) {
    if (job.status === 'VERIFYING') {
      width = 95;
    } else {
      width = 0;
    }
  }

  return (
    <article className="job-row">
      <span className={'job-icon ' + iconState}>
        {complete ? <CheckCircle2 size={17} /> : failed ? <CircleAlert size={17} /> : <ArrowDownToLine size={17} />}
      </span>
      <div className="job-file"><strong>{job.targetFile}</strong><small>{job.message}</small></div>
      <div className="job-progress">
        <div className="progress-track"><span className={barState} style={{ width: width + '%' }} /></div>
        <small>{job.progress !== undefined && job.progress !== null ? job.progress : 0}%</small>
      </div>
      <div className="job-actions">
        <StatusPill status={job.status} />
        {canCancel && (
          <button
            className="job-cancel"
            type="button"
            onClick={function () {
              onCancel(job.id);
            }}
            disabled={canceling}
            aria-busy={Boolean(canceling)}
            aria-label={(canceling ? 'Cancelling ' : 'Cancel ') + job.targetFile}
            title={canceling ? 'Cancelling download' : 'Cancel download'}
          >
            <X size={13} />
          </button>
        )}
      </div>
    </article>
  );
}
