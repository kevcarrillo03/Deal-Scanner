import { Platform } from 'react-native';
import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';

const DEVICE_ID_KEY = 'device_id';

let deviceIdPromise: Promise<string> | null = null;

export function getDeviceId(): Promise<string> {
  deviceIdPromise ??= loadOrCreateDeviceId().catch((error) => {
    deviceIdPromise = null;
    throw error;
  });
  return deviceIdPromise;
}

async function loadOrCreateDeviceId(): Promise<string> {
  const saved = await readItem(DEVICE_ID_KEY);
  if (saved) return saved;

  const created = Crypto.randomUUID();
  await writeItem(DEVICE_ID_KEY, created);
  return created;
}

async function readItem(key: string): Promise<string | null> {
  if (Platform.OS === 'web') {
    return window.localStorage.getItem(key);
  }
  return SecureStore.getItemAsync(key);
}

async function writeItem(key: string, value: string): Promise<void> {
  if (Platform.OS === 'web') {
    window.localStorage.setItem(key, value);
    return;
  }
  await SecureStore.setItemAsync(key, value);
}
