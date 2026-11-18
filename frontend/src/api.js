export async function post(path, body) {
  try {
    await fetch(`/api/sim/${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    /* connection state is reported by the SSE stream */
  }
}
