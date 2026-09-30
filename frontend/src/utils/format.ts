export function formatPrice(price: number) {
  return `$${price.toFixed(2)}`;
}

export function formatWhen(iso: string) {
  const date = new Date(iso);
  const time = date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const isToday = date.toDateString() === new Date().toDateString();
  return isToday ? `today at ${time}` : `${formatDay(iso)} at ${time}`;
}

export function formatDay(iso: string) {
  return new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric' });
}
