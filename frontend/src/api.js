export async function fetchGraph() {
  const res = await fetch('/api/graph', { cache: 'no-store' });
  if (!res.ok) {
    throw new Error(`GET /api/graph -> HTTP ${res.status}`);
  }
  return res.json();
}
