import { Trash2 } from 'lucide-react';
import JobRow from './JobRow';

export default function ActivityPanel({ jobs, visibleCount = 6, onClear }) {
  return (
    <section className="panel activity-panel">
      <div className="section-heading activity-heading">
        <div><p className="eyebrow">LIVE QUEUE</p><h2>Transfer activity</h2></div>
        <div className="activity-actions">
          {jobs.length > 0 && (
            <button className="history-clear" onClick={onClear} title="Clear completed and failed transfers">
              <Trash2 size={13} /><span>Clear history</span>
            </button>
          )}
          <span className="activity-count">{jobs.length} JOB{jobs.length === 1 ? '' : 'S'}</span>
        </div>
      </div>
      {jobs.length === 0 ? (
        <div className="empty-activity"><span className="activity-line" /><span>No transfers yet. Downloads will appear here with live progress.</span></div>
      ) : (
        <div className="job-list">
          {jobs.slice(0, visibleCount).map((job) => <JobRow key={job.id} job={job} />)}
        </div>
      )}
    </section>
  );
}
