// Generic HTTP helper. Knows nothing about Openwire endpoints.
export async function request(path, options = {}) {
  const response = await fetch(path, options);
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json')
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message =
      (typeof body === 'string' ? body : body.message || body.error) ||
      `Request failed (${response.status})`;
    throw new Error(message);
  }
  return body;
}

export const jsonPost = (path, payload) =>
  request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
