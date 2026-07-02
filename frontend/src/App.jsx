import React, { useEffect, useMemo, useState } from 'react';
import GraphView from './GraphView.jsx';
import { fetchGraph } from './api.js';

const POLL_MS = 5000;

function StatPill({ label, count, className }) {
  return (
    <div className={`stat-pill ${className}`}>
      <span className="stat-count">{count}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

function summarize(graph) {
  const counts = { up: 0, degraded: 0, down: 0, unknown: 0 };
  if (!graph) return counts;
  for (const url of Object.keys(graph.statusByUrl)) {
    const s = graph.statusByUrl[url];
    const key = (s && s.state ? s.state : 'UNKNOWN').toLowerCase();
    counts[key] = (counts[key] || 0) + 1;
  }
  return counts;
}

function statusClass(status) {
  if (!status || status.state === 'UNKNOWN') return 'unknown';
  return status.state.toLowerCase();
}

function statusLabel(status) {
  if (!status || status.state === 'UNKNOWN') return 'checking...';
  return status.statusCode ?? status.errorReason ?? 'checking...';
}

function StatusPill({ status }) {
  return <span className={`status-badge ${statusClass(status)}`}>{statusLabel(status)}</span>;
}

function CallRow({ edge, otherServiceLabel }) {
  return (
    <div className="call-row">
      <div className="call-row-main">
        <span className="call-row-name">{otherServiceLabel}</span>
        <StatusPill status={edge.status} />
      </div>
      <div className="call-row-meta">
        <code className="call-row-key">{edge.keyPath}</code>
        {edge.status && edge.status.latencyMs >= 0 && (
          <span className="call-row-latency">{edge.status.latencyMs} ms</span>
        )}
      </div>
      <a href={edge.url} target="_blank" rel="noreferrer" className="call-row-url">{edge.url}</a>
    </div>
  );
}

function NodeDetail({ info, onClose }) {
  const { node, outbound, inbound } = info;
  const isCountry = node.kind === 'COUNTRY';
  return (
    <div className="detail-panel">
      <button className="close-btn" onClick={onClose} aria-label="Close">&times;</button>
      <h3>{node.label}</h3>
      <p className="muted">
        {isCountry ? 'Country / partner endpoint' : 'Service'} &middot; {outbound.length} call{outbound.length === 1 ? '' : 's'} &middot; {inbound.length} caller{inbound.length === 1 ? '' : 's'}
      </p>

      <div className="detail-section">
        <div className="detail-section-title">Calls ({outbound.length})</div>
        <div className="call-list">
          {outbound.length === 0 && <p className="muted small">No outbound calls</p>}
          {outbound.map((e) => <CallRow key={e.id} edge={e} otherServiceLabel={e.target} />)}
        </div>
      </div>

      <div className="detail-section">
        <div className="detail-section-title">Called by ({inbound.length})</div>
        <div className="call-list">
          {inbound.length === 0 && <p className="muted small">No known callers</p>}
          {inbound.map((e) => <CallRow key={e.id} edge={e} otherServiceLabel={e.source} />)}
        </div>
      </div>
    </div>
  );
}

function EdgeDetail({ edge, onClose }) {
  return (
    <div className="detail-panel">
      <button className="close-btn" onClick={onClose} aria-label="Close">&times;</button>
      <h3>{edge.source} &rarr; {edge.target}</h3>
      <p className="muted">config key</p>
      <code className="key-path">{edge.keyPath}</code>
      <p className="muted" style={{ marginTop: 10 }}>endpoint</p>
      <a href={edge.url} target="_blank" rel="noreferrer" className="url-link">{edge.url}</a>
      <div className="status-row">
        <StatusPill status={edge.status} />
        {edge.status && edge.status.latencyMs >= 0 && (
          <span className="latency">{edge.status.latencyMs} ms</span>
        )}
      </div>
    </div>
  );
}

function DetailPanel({ selectedNodeInfo, selectedEdgeInfo, onClose }) {
  if (selectedNodeInfo) return <NodeDetail info={selectedNodeInfo} onClose={onClose} />;
  if (selectedEdgeInfo) return <EdgeDetail edge={selectedEdgeInfo} onClose={onClose} />;
  return null;
}

export default function App() {
  const [graph, setGraph] = useState(null);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [selectedNodeId, setSelectedNodeId] = useState(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const data = await fetchGraph();
        if (!cancelled) {
          setGraph(data);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
    }

    load();
    const id = setInterval(load, POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const filteredGraph = useMemo(() => {
    if (!graph || !search.trim()) return graph;
    const q = search.trim().toLowerCase();
    const keepNodeIds = new Set();
    graph.nodes.forEach((n) => {
      if (n.label.toLowerCase().includes(q)) keepNodeIds.add(n.id);
    });
    graph.edges.forEach((e) => {
      if (keepNodeIds.has(e.source) || keepNodeIds.has(e.target)) {
        keepNodeIds.add(e.source);
        keepNodeIds.add(e.target);
      }
    });
    return {
      ...graph,
      nodes: graph.nodes.filter((n) => keepNodeIds.has(n.id)),
      edges: graph.edges.filter((e) => keepNodeIds.has(e.source) && keepNodeIds.has(e.target))
    };
  }, [graph, search]);

  // Derived from the full (unfiltered) graph every render, so the panel stays
  // correct across the 5s poll and isn't affected by the search filter hiding
  // the selected item from view.
  const selectedNodeInfo = useMemo(() => {
    if (!selectedNodeId || !graph) return null;
    const node = graph.nodes.find((n) => n.id === selectedNodeId);
    if (!node) return null;
    const withStatus = (e) => ({ ...e, status: graph.statusByUrl[e.url] });
    return {
      node,
      outbound: graph.edges.filter((e) => e.source === selectedNodeId).map(withStatus),
      inbound: graph.edges.filter((e) => e.target === selectedNodeId).map(withStatus)
    };
  }, [selectedNodeId, graph]);

  const selectedEdgeInfo = useMemo(() => {
    if (!selectedEdgeId || !graph) return null;
    const edge = graph.edges.find((e) => e.id === selectedEdgeId);
    if (!edge) return null;
    return { ...edge, status: graph.statusByUrl[edge.url] };
  }, [selectedEdgeId, graph]);

  const counts = summarize(graph);

  const selectNode = (id) => {
    setSelectedNodeId(id);
    setSelectedEdgeId(null);
  };
  const selectEdge = (id) => {
    setSelectedEdgeId(id);
    setSelectedNodeId(null);
  };
  const closeDetail = () => {
    setSelectedNodeId(null);
    setSelectedEdgeId(null);
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="title-block">
          <h1>API Flow Dashboard</h1>
          <span className="subtitle">who calls whom, live from Vault</span>
        </div>

        <input
          className="search-box"
          placeholder="Filter by service name..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />

        <div className="stats">
          <StatPill label="up" count={counts.up} className="up" />
          <StatPill label="degraded" count={counts.degraded} className="degraded" />
          <StatPill label="down" count={counts.down} className="down" />
          <StatPill label="unchecked" count={counts.unknown} className="unknown" />
        </div>

        <div className="refresh-info">
          <span className="live-indicator"><span className="live-dot" />live</span>
          {graph && <span>updated {new Date(graph.generatedAt).toLocaleTimeString()}</span>}
        </div>
      </header>

      {error && <div className="error-banner">Failed to load graph: {error}</div>}

      <main className="main-area">
        {filteredGraph ? (
          <GraphView graph={filteredGraph} onSelectNode={selectNode} onSelectEdge={selectEdge} />
        ) : (
          <div className="loading">Loading graph...</div>
        )}

        <DetailPanel
          selectedNodeInfo={selectedNodeInfo}
          selectedEdgeInfo={selectedEdgeInfo}
          onClose={closeDetail}
        />

        <div className="legend">
          <div className="legend-title">Legend</div>
          <div><span className="dot up" /> 2xx - up</div>
          <div><span className="dot degraded" /> 3xx/401/403/404 - degraded</div>
          <div><span className="dot down" /> 5xx/timeout/dns/tls - down</div>
          <div><span className="dot unknown" /> not checked yet</div>
          <div className="legend-node"><span className="box country" /> country / partner endpoint</div>
        </div>
      </main>
    </div>
  );
}
