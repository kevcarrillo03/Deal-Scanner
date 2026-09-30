import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

export default function RootLayout() {
  return (
    <>
      <StatusBar style="auto" />
      <Stack>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="results/[upc]" options={{ title: 'Prices', headerBackTitle: 'Back' }} />
        <Stack.Screen name="history" options={{ title: 'Recent Scans', headerBackTitle: 'Scan' }} />
      </Stack>
    </>
  );
}
