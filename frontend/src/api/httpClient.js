export async function request(path, options) {
  if (options === undefined) {
    options = {};
  }

  const response = await fetch(path, options);
  const contentType = response.headers.get('content-type') || '';

  let body;
  if (contentType.indexOf('application/json') !== -1) {
    body = await response.json();
  } else {
    body = await response.text();
  }

  if (!response.ok) {
    const message = typeof body === 'string' ? body : (body.message || body.error || ('Request failed (' + response.status + ')'));
    throw new Error(message);
  }

  return body;
}

export function jsonPost(path, payload) {
  return request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
}
