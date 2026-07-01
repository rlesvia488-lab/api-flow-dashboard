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

function DetailPanel({ selectedNode, selectedEdge, onClose }) {
  if (!selectedNode && !selectedEdge) return null;
  return (
    <div className="detail-panel">
      <button className="close-btn" onClick={onClose}>x</button>
      {selectedNode && (
        <>
          <h3>{selectedNode.label.split('\n')[0]}</h3>
          <p className="muted">{selectedNode.id.startsWith('external:') ? 'External / unresolved dependency' : 'Internal service'}</p>
          {selectedNode.health && selectedNode.health !== 'none' && (
            <span className={`status-badge ${selectedNode.health}`}>{selectedNode.health}</span>
          )}
        </>
      )}
      {selectedEdge && (
        <>
          <h3>{selectedEdge.source} &rarr; {selectedEdge.target}</h3>
          <p className="muted">config key</p>
          <code className="key-path">{selectedEdge.keyPath}</code>
          <p className="muted" style={{ marginTop: 10 }}>endpoint</p>
          <a href={selectedEdge.url} target="_blank" rel="noreferrer" className="url-link">{selectedEdge.url}</a>
          <div className="status-row">
            <span className={`status-badge ${(selectedEdge.errorReason && !selectedEdge.statusCode) ? 'down' : (selectedEdge.statusCode >= 200 && selectedEdge.statusCode < 300 ? 'up' : selectedEdge.statusCode ? 'degraded' : 'unknown')}`}>
              {selectedEdge.statusCode ?? selectedEdge.errorReason ?? 'checking...'}
            </span>
            {selectedEdge.latencyMs != null && selectedEdge.latencyMs >= 0 && (
              <span className="latency">{selectedEdge.latencyMs} ms</span>
            )}
          </div>
        </>
      )}
    </div>
  );
}

export default function App() {
  const [graph, setGraph] = useState(null);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [selectedNode, setSelectedNode] = useState(null);
  const [selectedEdge, setSelectedEdge] = useState(null);

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

  const counts = summarize(graph);

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
          <GraphView graph={filteredGraph} onSelectNode={setSelectedNode} onSelectEdge={setSelectedEdge} />
        ) : (
          <div className="loading">Loading graph...</div>
        )}

        <DetailPanel
          selectedNode={selectedNode}
          selectedEdge={selectedEdge}
          onClose={() => { setSelectedNode(null); setSelectedEdge(null); }}
        />

        <div className="legend">
          <div className="legend-title">Legend</div>
          <div><span className="dot up" /> 2xx - up</div>
          <div><span className="dot degraded" /> 3xx/401/403/404 - degraded</div>
          <div><span className="dot down" /> 5xx/timeout/dns/tls - down</div>
          <div><span className="dot unknown" /> not checked yet</div>
          <div className="legend-node"><span className="box internal" /> internal service</div>
          <div className="legend-node"><span className="box external" /> external / unresolved</div>
        </div>
      </main>
    </div>
  );
}
