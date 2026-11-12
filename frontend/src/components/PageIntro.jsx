export default function PageIntro(props) {
  const { trackerPort, peerPort } = props;

  const signalLines = [];
  for (let i = 0; i < 37; i += 1) {
    signalLines.push(<i key={i} />);
  }

  return (
    <section className="page-intro">
      <div>
        <p className="eyebrow">PEER-TO-PEER / NODE 01</p>
        <h1>File exchange, <span>in motion.</span></h1>
        <p className="intro-copy">Share directly. Find a peer. Keep every transfer verifiable.</p>
      </div>
      <div className="intro-signal" aria-hidden="true">
        <div className="signal-label"><span>LOCAL NODE</span><span>01 / ACTIVE</span></div>
        <div className="signal-lines">{signalLines}</div>
        <div className="signal-footer"><span>UDP : {trackerPort}</span><span>TCP : {peerPort}</span></div>
      </div>
    </section>
  );
}
