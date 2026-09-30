import { getDeviceId } from './deviceId';

export type StorePrice = {
  store: string;
  price: number;
  title: string;
  link: string;
  thumbnail: string | null;
};

export type ScanResult = {
  upc: string;
  product_name: string;
  prices: StorePrice[];
  fetched_at: string;
};

export type PricePoint = {
  store: string;
  price: number;
};

export type RecentScan = {
  upc: string;
  product_name: string;
  image_url: string | null;
  last_scanned_at: string;
  best_price: PricePoint | null;
};

export type PriceHistory = {
  upc: string;
  product_name: string;
  days: number;
  lowest_price: (PricePoint & { checked_at: string }) | null;
  checks: { checked_at: string; prices: PricePoint[] }[];
};

export class ScanError extends Error {}

const TIMEOUT_MS = 90000;

export async function fetchScan(upc: string, signal?: AbortSignal): Promise<ScanResult> {
  const response = await request(`/api/v1/scan/${encodeURIComponent(upc)}`, signal);

  if (response.status === 404) throw new ScanError("We couldn't find a product for this barcode.");
  if (response.status === 429) throw new ScanError('Daily lookup limit reached. Try again tomorrow.');
  if (!response.ok) throw new ScanError(`Something went wrong looking up this barcode (error ${response.status}).`);

  return response.json();
}

export async function fetchRecentScans(signal?: AbortSignal): Promise<RecentScan[]> {
  const response = await request('/api/v1/scans/recent', signal);

  if (!response.ok) throw new ScanError(`Couldn't load your recent scans (error ${response.status}).`);

  return response.json();
}

export async function fetchPriceHistory(upc: string, days: number, signal?: AbortSignal): Promise<PriceHistory | null> {
  const response = await request(`/api/v1/products/${encodeURIComponent(upc)}/history?days=${days}`, signal);

  if (response.status === 404) return null;
  if (!response.ok) throw new ScanError(`Couldn't load price history (error ${response.status}).`);

  return response.json();
}

async function request(path: string, signal?: AbortSignal): Promise<Response> {
  const url = `${process.env.EXPO_PUBLIC_API_URL}${path}`;

  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), TIMEOUT_MS);
  signal?.addEventListener('abort', () => timeout.abort());

  try {
    const headers: Record<string, string> = {};
    try {
      headers['X-Device-Id'] = await getDeviceId();
    } catch (error) {
      console.warn("couldn't load device id, sending request without it: ", error);
    }

    return await fetch(url, { signal: timeout.signal, headers });
  } catch (error) {
    if (signal?.aborted) throw error;
    console.error('error connecting to backend: ', error);
    throw new ScanError("Couldn't reach the server. Make sure the backend is running and your phone is on the same Wi-Fi.");
  } finally {
    clearTimeout(timer);
  }
}
