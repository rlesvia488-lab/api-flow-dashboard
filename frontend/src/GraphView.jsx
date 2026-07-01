import React, { useEffect, useRef } from 'react';
import cytoscape from 'cytoscape';
import dagre from 'cytoscape-dagre';

cytoscape.use(dagre);

const COLORS = {
  up: '#2fdd6b',
  degraded: '#ffb020',
  down: '#ff4d4f',
  unknown: '#6b7488'
};

function healthClass(status) {
  if (!status || status.state === 'UNKNOWN') return 'unknown';
  return status.state.toLowerCase(); // up | degraded | down
}

function edgeLabel(status) {
  if (!status || status.state === 'UNKNOWN') return '···';
  if (status.statusCode != null) return String(status.statusCode);
  return status.errorReason || '?';
}

/** Worst-case aggregate across a node's outbound edges: down > degraded > up > unknown > none. */
function aggregateNodeHealth(nodeId, edges, statusByUrl) {
  const outbound = edges.filter((e) => e.source === nodeId);
  if (outbound.length === 0) return { cls: 'none', up: 0, degraded: 0, down: 0 };

  let up = 0, degraded = 0, down = 0, unknown = 0;
  for (const e of outbound) {
    const cls = healthClass(statusByUrl[e.url]);
    if (cls === 'up') up++;
    else if (cls === 'degraded') degraded++;
    else if (cls === 'down') down++;
    else unknown++;
  }
  const cls = down > 0 ? 'down' : degraded > 0 ? 'degraded' : unknown > 0 && up === 0 ? 'unknown' : 'up';
  return { cls, up, degraded, down };
}

function buildElements(graph) {
  const nodes = graph.nodes.map((n) => {
    const agg = aggregateNodeHealth(n.id, graph.edges, graph.statusByUrl);
    const summary = agg.cls === 'none' ? '' : `${agg.up} up  ${agg.degraded ? agg.degraded + ' deg  ' : ''}${agg.down ? agg.down + ' down' : ''}`.trim();
    return {
      data: {
        id: n.id,
        label: summary ? `${n.label}\n${summary}` : n.label,
        health: agg.cls
      },
      classes: [n.type === 'EXTERNAL' ? 'external' : 'internal', `health-${agg.cls}`].join(' ')
    };
  });

  const edges = graph.edges.map((e) => {
    const status = graph.statusByUrl[e.url];
    const cls = healthClass(status);
    return {
      data: {
        id: e.id,
        source: e.source,
        target: e.target,
        label: edgeLabel(status),
        url: e.url,
        keyPath: e.keyPath,
        statusCode: status ? status.statusCode : null,
        errorReason: status ? status.errorReason : null,
        latencyMs: status ? status.latencyMs : null
      },
      classes: cls
    };
  });

  return [...nodes, ...edges];
}

const STYLE = [
  {
    selector: 'node',
    style: {
      label: 'data(label)',
      'text-valign': 'center',
      'text-halign': 'center',
      'font-size': 12,
      'font-weight': 500,
      color: '#eef1f8',
      'background-color': '#141b30',
      'background-gradient-stop-colors': '#1b2542 #0e1424',
      'background-gradient-direction': 'to-bottom-right',
      'border-width': 2.5,
      'border-color': COLORS.unknown,
      shape: 'round-rectangle',
      padding: '14px',
      width: 'label',
      height: 'label',
      'text-wrap': 'wrap',
      'text-max-width': '130px',
      'line-height': 1.5,
      'shadow-blur': 18,
      'shadow-opacity': 0.55,
      'shadow-color': COLORS.unknown,
      'shadow-offset-x': 0,
      'shadow-offset-y': 0,
      'transition-property': 'border-color, shadow-color, shadow-blur',
      'transition-duration': 300
    }
  },
  { selector: 'node.health-up', style: { 'border-color': COLORS.up, 'shadow-color': COLORS.up } },
  { selector: 'node.health-degraded', style: { 'border-color': COLORS.degraded, 'shadow-color': COLORS.degraded } },
  { selector: 'node.health-down', style: { 'border-color': COLORS.down, 'shadow-color': COLORS.down, 'shadow-blur': 26, 'shadow-opacity': 0.75 } },
  { selector: 'node.health-none', style: { 'border-color': '#3a4260', 'shadow-blur': 8, 'shadow-opacity': 0.3 } },
  {
    selector: 'node.external',
    style: {
      'background-color': '#232838',
      'background-gradient-stop-colors': '#262c3f #171b28',
      'border-style': 'dashed',
      color: '#b7bccb'
    }
  },
  {
    selector: 'node:selected',
    style: { 'border-width': 4, 'border-color': '#ffd43b', 'shadow-color': '#ffd43b', 'shadow-blur': 30, 'shadow-opacity': 0.9 }
  },
  {
    selector: 'edge',
    style: {
      width: 3,
      'curve-style': 'bezier',
      'target-arrow-shape': 'triangle',
      'arrow-scale': 1.2,
      label: 'data(label)',
      'font-size': 10,
      'font-weight': 700,
      color: '#0b0f19',
      'text-background-opacity': 1,
      'text-background-shape': 'round-rectangle',
      'text-background-padding': '4px',
      'text-rotation': 'autorotate',
      'shadow-blur': 12,
      'shadow-opacity': 0.6,
      'shadow-offset-x': 0,
      'shadow-offset-y': 0
    }
  },
  {
    selector: 'edge.up',
    style: {
      'line-color': COLORS.up, 'target-arrow-color': COLORS.up, 'text-background-color': COLORS.up,
      color: '#06210c', 'shadow-color': COLORS.up,
      'line-style': 'dashed', 'line-dash-pattern': [8, 6]
    }
  },
  {
    selector: 'edge.degraded',
    style: {
      'line-color': COLORS.degraded, 'target-arrow-color': COLORS.degraded, 'text-background-color': COLORS.degraded,
      color: '#231600', 'shadow-color': COLORS.degraded,
      'line-style': 'dashed', 'line-dash-pattern': [8, 6]
    }
  },
  {
    selector: 'edge.down',
    style: {
      'line-color': COLORS.down, 'target-arrow-color': COLORS.down, 'text-background-color': COLORS.down,
      color: '#2b0000', 'shadow-color': COLORS.down, 'shadow-blur': 16, 'shadow-opacity': 0.8,
      'line-style': 'dashed', 'line-dash-pattern': [3, 4], width: 3.5
    }
  },
  {
    selector: 'edge.unknown',
    style: {
      'line-color': COLORS.unknown, 'target-arrow-color': COLORS.unknown, 'text-background-color': COLORS.unknown,
      color: '#fff', 'shadow-opacity': 0.2, 'line-style': 'dotted'
    }
  },
  {
    selector: 'edge:selected',
    style: { width: 5 }
  }
];

export default function GraphView({ graph, onSelectEdge, onSelectNode }) {
  const containerRef = useRef(null);
  const cyRef = useRef(null);
  const rafRef = useRef(null);

  useEffect(() => {
    const cy = cytoscape({
      container: containerRef.current,
      style: STYLE,
      wheelSensitivity: 0.2
    });
    cyRef.current = cy;

    cy.on('tap', 'edge', (evt) => onSelectEdge && onSelectEdge(evt.target.data()));
    cy.on('tap', 'node', (evt) => onSelectNode && onSelectNode(evt.target.data()));
    cy.on('tap', (evt) => {
      if (evt.target === cy) {
        onSelectEdge && onSelectEdge(null);
        onSelectNode && onSelectNode(null);
      }
    });

    // Marching-ants traffic flow: only "live" (up/degraded) edges animate,
    // down/unresolved edges stay static so motion itself reads as "traffic is flowing".
    let offset = 0;
    const animate = () => {
      offset -= 0.6;
      cy.style().selector('edge.up, edge.degraded').style({ 'line-dash-offset': offset }).update();
      rafRef.current = requestAnimationFrame(animate);
    };
    rafRef.current = requestAnimationFrame(animate);

    return () => {
      cancelAnimationFrame(rafRef.current);
      cy.destroy();
    };
  }, []);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy || !graph) return;

    const newElements = buildElements(graph);
    const newIds = new Set(newElements.map((el) => el.data.id));
    const existingIds = new Set(cy.elements().map((el) => el.id()));
    const isFirstLoad = existingIds.size === 0;

    let structureChanged = newIds.size !== existingIds.size;
    if (!structureChanged) {
      for (const id of newIds) {
        if (!existingIds.has(id)) {
          structureChanged = true;
          break;
        }
      }
    }

    if (structureChanged) {
      // Topology actually changed (service/edge added or removed) - rebuild and
      // re-layout. Only snap the viewport with fit() on the very first load;
      // afterwards a growing graph shouldn't yank a zoomed-in user back out.
      cy.elements().forEach((el) => {
        if (!newIds.has(el.id())) el.remove();
      });
      newElements.forEach((el) => {
        const existing = cy.getElementById(el.data.id);
        if (existing.length > 0) {
          existing.data(el.data);
          existing.classes(el.classes);
        } else {
          cy.add(el);
        }
      });
      cy.layout({ name: 'dagre', rankDir: 'LR', nodeSep: 55, rankSep: 130, edgeSep: 25 }).run();
      if (isFirstLoad) {
        cy.fit(undefined, 40);
      }
    } else {
      // Same nodes/edges, only health/status data refreshed on this poll - update
      // in place so the user's current pan/zoom is left completely untouched.
      newElements.forEach((el) => {
        const existing = cy.getElementById(el.data.id);
        if (existing.length > 0) {
          existing.data(el.data);
          existing.classes(el.classes);
        }
      });
    }
  }, [graph]);

  const zoomBy = (factor) => {
    const cy = cyRef.current;
    if (!cy) return;
    const level = Math.max(0.02, Math.min(6, cy.zoom() * factor));
    cy.zoom({ level, renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 } });
  };

  const fitToScreen = () => {
    cyRef.current && cyRef.current.fit(undefined, 40);
  };

  return (
    <div className="graph-wrapper">
      <div ref={containerRef} className="graph-canvas" />
      <div className="zoom-controls">
        <button onClick={() => zoomBy(1.3)} title="Zoom in" aria-label="Zoom in">+</button>
        <button onClick={() => zoomBy(1 / 1.3)} title="Zoom out" aria-label="Zoom out">&minus;</button>
        <button onClick={fitToScreen} title="Fit to screen" aria-label="Fit to screen">&#x26F6;</button>
      </div>
    </div>
  );
}
