import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

const BARCODE_PATTERN = /^\d{8,14}$/;

export function BarcodeEntry({ onSubmit, onCancel }: { onSubmit: (upc: string) => void; onCancel?: () => void }) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = () => {
    const upc = value.replace(/\s/g, '');
    if (!BARCODE_PATTERN.test(upc)) {
      setError('Enter the 8 to 14 digit number printed under the barcode.');
      return;
    }
    setError(null);
    setValue('');
    onSubmit(upc);
  };

  return (
    <View style={styles.panel}>
      <Text style={styles.label}>Type a barcode</Text>
      <View style={styles.row}>
        <TextInput
          style={styles.input}
          value={value}
          onChangeText={(text) => {
            setValue(text.replace(/[^\d\s]/g, ''));
            setError(null);
          }}
          onSubmitEditing={submit}
          placeholder="e.g. 028400183826"
          placeholderTextColor="#999"
          keyboardType="number-pad"
          inputMode="numeric"
          returnKeyType="search"
          maxLength={20}
          autoFocus
        />
        <Pressable style={({ pressed }) => [styles.button, pressed && styles.pressed]} onPress={submit}>
          <Text style={styles.buttonText}>Look up</Text>
        </Pressable>
      </View>
      {error && <Text style={styles.error}>{error}</Text>}
      {onCancel && (
        <Pressable onPress={onCancel} hitSlop={8}>
          <Text style={styles.cancel}>Cancel</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 14,
    gap: 10,
  },
  label: {
    fontSize: 15,
    fontWeight: '600',
    color: '#111',
  },
  row: {
    flexDirection: 'row',
    gap: 8,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#d1d1d6',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 17,
    color: '#111',
  },
  button: {
    backgroundColor: '#16a34a',
    borderRadius: 10,
    paddingHorizontal: 16,
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  error: {
    fontSize: 13,
    color: '#dc2626',
  },
  cancel: {
    fontSize: 15,
    color: '#555',
    textAlign: 'center',
  },
});
