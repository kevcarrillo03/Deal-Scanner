import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Button, FlatList, Image, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';

import { fetchRecentScans, RecentScan, ScanError } from '@/api/scan';
import { useIsSlow } from '@/hooks/useIsSlow';
import { formatPrice, formatWhen } from '@/utils/format';

export default function HistoryScreen() {
  const [scans, setScans] = useState<RecentScan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const isSlow = useIsSlow(!scans && !error);

  const load = useCallback(async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    try {
      const recent = await fetchRecentScans(controller.signal);
      setScans(recent);
      setError(null);
    } catch (err) {
      if (controller.signal.aborted) return;
      setError(err instanceof ScanError ? err.message : 'Something went wrong. Please try again.');
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
      return () => controllerRef.current?.abort();
    }, [load])
  );

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  if (error && !scans) {
    return (
      <View style={styles.centered}>
        <Text style={styles.message}>{error}</Text>
        <Button title="Try again" onPress={load} />
      </View>
    );
  }

  if (!scans) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" />
        {isSlow && <Text style={styles.message}>Waking up the server, this can take up to a minute the first time.</Text>}
      </View>
    );
  }

  return (
    <FlatList
      style={styles.list}
      contentContainerStyle={scans.length === 0 ? styles.emptyContent : styles.listContent}
      data={scans}
      keyExtractor={(item) => item.upc}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      ListEmptyComponent={
        <View style={styles.empty}>
          <Text style={styles.message}>No scans yet. Products you scan will show up here.</Text>
          <Button title="Start scanning" onPress={() => router.back()} />
        </View>
      }
      renderItem={({ item }) => <ScanRow scan={item} />}
    />
  );
}

function ScanRow({ scan }: { scan: RecentScan }) {
  return (
    <Pressable
      style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
      onPress={() => router.push({ pathname: '/results/[upc]', params: { upc: scan.upc } })}
    >
      {scan.image_url ? (
        <Image source={{ uri: scan.image_url }} style={styles.image} resizeMode="contain" />
      ) : (
        <View style={[styles.image, styles.imagePlaceholder]} />
      )}
      <View style={styles.rowText}>
        <Text style={styles.name} numberOfLines={2}>{scan.product_name}</Text>
        <Text style={[styles.detail, !scan.best_price && styles.detailMuted]}>
          {scan.best_price
            ? `${formatPrice(scan.best_price.price)} at ${scan.best_price.store}`
            : 'No prices found'}
        </Text>
        <Text style={styles.when}>Scanned {formatWhen(scan.last_scanned_at)}</Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 12,
    backgroundColor: '#f5f5f7',
  },
  message: {
    fontSize: 16,
    textAlign: 'center',
    color: '#555',
  },
  list: {
    backgroundColor: '#f5f5f7',
  },
  listContent: {
    padding: 16,
    gap: 10,
  },
  emptyContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
  },
  empty: {
    alignItems: 'center',
    gap: 12,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 12,
    gap: 12,
    borderWidth: 1,
    borderColor: '#e5e5ea',
  },
  rowPressed: {
    opacity: 0.6,
  },
  image: {
    width: 56,
    height: 56,
    borderRadius: 8,
    backgroundColor: '#fff',
  },
  imagePlaceholder: {
    backgroundColor: '#e5e5ea',
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  name: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111',
  },
  detail: {
    fontSize: 14,
    color: '#16a34a',
    fontWeight: '600',
  },
  detailMuted: {
    color: '#888',
    fontWeight: '400',
  },
  when: {
    fontSize: 12,
    color: '#888',
  },
});
