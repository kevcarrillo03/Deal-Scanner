import { useEffect, useState } from 'react';
import { ActivityIndicator, Button, FlatList, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import * as Linking from 'expo-linking';
import { router, useLocalSearchParams } from 'expo-router';

import { fetchPriceHistory, fetchScan, PriceHistory, ScanError, ScanResult, StorePrice } from '@/api/scan';
import { useIsSlow } from '@/hooks/useIsSlow';
import { formatDay, formatPrice, formatWhen } from '@/utils/format';

const HISTORY_DAYS = 30;
const HISTORY_ROWS = 5;

export default function ResultsScreen() {
  const { upc } = useLocalSearchParams<{ upc: string }>();
  const [result, setResult] = useState<ScanResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<PriceHistory | null>(null);
  const isSlow = useIsSlow(!result && !error);

  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    setError(null);
    setHistory(null);

    fetchScan(upc, controller.signal)
      .then((scan) => {
        setResult(scan);
        fetchPriceHistory(upc, HISTORY_DAYS, controller.signal)
          .then(setHistory)
          .catch(() => {});
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        setError(err instanceof ScanError ? err.message : 'Something went wrong. Please try again.');
      });

    return () => controller.abort();
  }, [upc]);

  if (error) {
    return (
      <View style={styles.centered}>
        <Text style={styles.errorText}>{error}</Text>
        <Text style={styles.upc}>Barcode: {upc}</Text>
        <Button title="Scan again" onPress={() => router.back()} />
      </View>
    );
  }

  if (!result) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" />
        <Text style={styles.loadingText}>Finding the best prices…</Text>
        {isSlow && <Text style={styles.slowText}>Waking up the server, this can take up to a minute the first time.</Text>}
      </View>
    );
  }

  const productImage = result.prices.find((p) => p.thumbnail)?.thumbnail;

  return (
    <FlatList
      style={styles.list}
      contentContainerStyle={styles.listContent}
      data={result.prices}
      keyExtractor={(item) => item.store}
      ListHeaderComponent={
        <View style={styles.header}>
          {productImage && <Image source={{ uri: productImage }} style={styles.productImage} resizeMode="contain" />}
          <Text style={styles.productName}>{result.product_name}</Text>
          <Text style={styles.upc}>Barcode: {result.upc}</Text>
          <Text style={styles.upc}>Prices as of {formatWhen(result.fetched_at)}</Text>
        </View>
      }
      ListEmptyComponent={
        <Text style={styles.emptyText}>No prices found at major stores for this product.</Text>
      }
      renderItem={({ item, index }) => <PriceRow price={item} isBest={index === 0} />}
      ListFooterComponent={
        <View style={styles.footer}>
          {history && <PriceHistorySection history={history} />}
          <Button title="Scan another item" onPress={() => router.back()} />
        </View>
      }
    />
  );
}

function PriceHistorySection({ history }: { history: PriceHistory }) {
  if (history.checks.length < 2) {
    return <Text style={styles.historyNote}>Price history will build up as this product's prices are checked over time.</Text>;
  }

  return (
    <View style={styles.historyCard}>
      <Text style={styles.historyTitle}>Price history ({history.days} days)</Text>
      {history.lowest_price && (
        <Text style={styles.historyLowest}>
          Lowest: {formatPrice(history.lowest_price.price)} at {history.lowest_price.store} on {formatDay(history.lowest_price.checked_at)}
        </Text>
      )}
      {history.checks.slice(0, HISTORY_ROWS).map((check) => {
        const best = check.prices[0];
        return (
          <View key={check.checked_at} style={styles.historyRow}>
            <Text style={styles.historyDate}>{formatDay(check.checked_at)}</Text>
            <Text style={styles.historyPrice}>{best ? `${formatPrice(best.price)} at ${best.store}` : 'No prices found'}</Text>
          </View>
        );
      })}
    </View>
  );
}

function PriceRow({ price, isBest }: { price: StorePrice; isBest: boolean }) {
  return (
    <Pressable
      style={({ pressed }) => [styles.row, isBest && styles.bestRow, pressed && styles.rowPressed]}
      onPress={() => price.link && Linking.openURL(price.link)}
      disabled={!price.link}
    >
      <View style={styles.rowText}>
        <View style={styles.storeLine}>
          <Text style={styles.store}>{price.store}</Text>
          {isBest && <Text style={styles.bestBadge}>Best price</Text>}
        </View>
        <Text style={styles.listingTitle} numberOfLines={2}>{price.title}</Text>
      </View>
      <Text style={[styles.price, isBest && styles.bestPrice]}>{formatPrice(price.price)}</Text>
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
  loadingText: {
    fontSize: 16,
    color: '#555',
  },
  slowText: {
    fontSize: 14,
    color: '#888',
    textAlign: 'center',
  },
  errorText: {
    fontSize: 17,
    textAlign: 'center',
    color: '#222',
  },
  list: {
    backgroundColor: '#f5f5f7',
  },
  listContent: {
    padding: 16,
    gap: 10,
  },
  header: {
    alignItems: 'center',
    marginBottom: 8,
    gap: 6,
  },
  productImage: {
    width: 140,
    height: 140,
    borderRadius: 12,
    backgroundColor: '#fff',
  },
  productName: {
    fontSize: 20,
    fontWeight: '600',
    textAlign: 'center',
    color: '#111',
  },
  upc: {
    fontSize: 13,
    color: '#888',
  },
  emptyText: {
    textAlign: 'center',
    fontSize: 16,
    color: '#555',
    marginTop: 24,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    gap: 12,
    borderWidth: 1,
    borderColor: '#e5e5ea',
  },
  bestRow: {
    borderColor: '#16a34a',
    borderWidth: 2,
  },
  rowPressed: {
    opacity: 0.6,
  },
  rowText: {
    flex: 1,
    gap: 4,
  },
  storeLine: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  store: {
    fontSize: 17,
    fontWeight: '600',
    color: '#111',
  },
  bestBadge: {
    fontSize: 12,
    fontWeight: '600',
    color: '#fff',
    backgroundColor: '#16a34a',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
    overflow: 'hidden',
  },
  listingTitle: {
    fontSize: 13,
    color: '#666',
  },
  price: {
    fontSize: 20,
    fontWeight: '700',
    color: '#111',
  },
  bestPrice: {
    color: '#16a34a',
  },
  footer: {
    marginTop: 12,
    gap: 16,
  },
  historyNote: {
    fontSize: 13,
    color: '#888',
    textAlign: 'center',
  },
  historyCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    gap: 8,
    borderWidth: 1,
    borderColor: '#e5e5ea',
  },
  historyTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111',
  },
  historyLowest: {
    fontSize: 14,
    fontWeight: '600',
    color: '#16a34a',
  },
  historyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  historyDate: {
    fontSize: 14,
    color: '#666',
  },
  historyPrice: {
    fontSize: 14,
    color: '#111',
  },
});
