import { useCallback, useRef, useState } from 'react';
import { StyleSheet, Text, View, Button, Dimensions, Pressable } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { router, useFocusEffect } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BarcodeEntry } from '@/components/BarcodeEntry';

export default function ScannerScreen() {
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);
  const [typing, setTyping] = useState(false);
  const isScanning = useRef(false);
  const insets = useSafeAreaInsets();

  useFocusEffect(
    useCallback(() => {
      isScanning.current = false;
      setScanned(false);
      setTyping(false);
    }, [])
  );

  const openResults = (upc: string) => {
    if (isScanning.current) return;
    isScanning.current = true;
    setScanned(true);

    router.push({ pathname: '/results/[upc]', params: { upc } });
  };

  if (!permission) {
    return <View style={styles.container} />;
  }

  if (!permission.granted) {
    return (
      <View style={[styles.container, styles.permissionContainer]}>
        <Text style={styles.message}>we need your permission to show the camera</Text>
        <Button onPress={requestPermission} title="grant permission" />
        <View style={styles.permissionEntry}>
          <BarcodeEntry onSubmit={openResults} />
        </View>
      </View>
    );
  }

  const handleBarCodeScanned = ({ data }: { data: string }) => openResults(data);

  return (
    <View style={styles.container}>
      <CameraView
        style={styles.camera}
        facing="back"
        barcodeScannerSettings={{
          barcodeTypes: ["upc_a", "upc_e", "ean13", "ean8"],
        }}
        onBarcodeScanned={scanned ? undefined : handleBarCodeScanned}
      />

      <View style={styles.scanGuide}>
        <View style={styles.dim} />
        <View style={styles.scanRow}>
          <View style={styles.dim} />
          <View style={[styles.scanBox, scanned && styles.scanBoxScanned]}>
            <View style={[styles.corner, styles.topLeft]} />
            <View style={[styles.corner, styles.topRight]} />
            <View style={[styles.corner, styles.bottomLeft]} />
            <View style={[styles.corner, styles.bottomRight]} />
          </View>
          <View style={styles.dim} />
        </View>
        <View style={styles.dim}>
          <Text style={styles.hint}>line up the barcode inside the box</Text>
        </View>
      </View>

      <Pressable
        style={({ pressed }) => [styles.historyButton, { top: insets.top + 12 }, pressed && styles.historyButtonPressed]}
        onPress={() => router.push('/history')}
      >
        <Text style={styles.historyButtonText}>History</Text>
      </Pressable>

      {!typing && (
        <Pressable
          style={({ pressed }) => [styles.historyButton, styles.typeButton, { top: insets.top + 12 }, pressed && styles.historyButtonPressed]}
          onPress={() => setTyping(true)}
        >
          <Text style={styles.historyButtonText}>Type barcode</Text>
        </Pressable>
      )}

      {typing && (
        <View style={[styles.entryPanel, { top: insets.top + 60 }]}>
          <BarcodeEntry onSubmit={openResults} onCancel={() => setTyping(false)} />
        </View>
      )}
    </View>
  );
}

const SCAN_BOX_WIDTH = Math.min(Dimensions.get('window').width * 0.8, 360);
const SCAN_BOX_HEIGHT = SCAN_BOX_WIDTH * 0.5;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    backgroundColor: '#000',
  },
  camera: {
    flex: 1,
  },
  scanGuide: {
    ...StyleSheet.absoluteFill,
    pointerEvents: 'none',
  },
  dim: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  scanRow: {
    flexDirection: 'row',
    height: SCAN_BOX_HEIGHT,
  },
  scanBox: {
    width: SCAN_BOX_WIDTH,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.4)',
  },
  scanBoxScanned: {
    borderColor: '#4ade80',
  },
  corner: {
    position: 'absolute',
    width: 28,
    height: 28,
    borderColor: '#fff',
  },
  topLeft: {
    top: -2,
    left: -2,
    borderTopWidth: 4,
    borderLeftWidth: 4,
    borderTopLeftRadius: 12,
  },
  topRight: {
    top: -2,
    right: -2,
    borderTopWidth: 4,
    borderRightWidth: 4,
    borderTopRightRadius: 12,
  },
  bottomLeft: {
    bottom: -2,
    left: -2,
    borderBottomWidth: 4,
    borderLeftWidth: 4,
    borderBottomLeftRadius: 12,
  },
  bottomRight: {
    bottom: -2,
    right: -2,
    borderBottomWidth: 4,
    borderRightWidth: 4,
    borderBottomRightRadius: 12,
  },
  hint: {
    color: '#fff',
    fontSize: 16,
    textAlign: 'center',
    marginTop: 20,
    paddingHorizontal: 20,
  },
  historyButton: {
    position: 'absolute',
    right: 16,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: 'rgba(0,0,0,0.6)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.4)',
  },
  typeButton: {
    right: undefined,
    left: 16,
  },
  entryPanel: {
    position: 'absolute',
    left: 16,
    right: 16,
  },
  permissionContainer: {
    padding: 24,
  },
  permissionEntry: {
    marginTop: 32,
  },
  historyButtonPressed: {
    opacity: 0.6,
  },
  historyButtonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '600',
  },
  message: {
    textAlign: 'center',
    paddingBottom: 20,
    fontSize: 16,
    color: '#fff',
    paddingHorizontal: 20,
  },
});
