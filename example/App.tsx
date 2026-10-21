import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { FlatList, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import {
  GlassButton,
  GlassContainer,
  GlassView,
  isGlassEffectAPIAvailable,
  isLiquidGlassAvailable,
  type GlassStyle,
} from 'react-native-glaze';

const AnimatedGlassView = Animated.createAnimatedComponent(GlassView);

type Row = { id: string; uri: string; title: string };

const ROWS: Row[] = Array.from({ length: 200 }, (_, i) => ({
  id: String(i),
  uri: `https://picsum.photos/seed/glaze${i}/600/360`,
  title: `Photo ${i + 1}`,
}));

const TABS = ['Scroll', 'Animated', 'Press'] as const;
type Tab = (typeof TABS)[number];

export default function App() {
  const [tab, setTab] = useState<Tab>('Scroll');
  const [style, setStyle] = useState<GlassStyle>('regular');
  const [log, setLog] = useState('Tap something');

  return (
    <GestureHandlerRootView style={styles.fill}>
      <View style={styles.fill}>
        <FlatList
          data={ROWS}
          keyExtractor={(r) => r.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <Image source={{ uri: item.uri }} style={styles.photo} />
              <Text style={styles.rowTitle}>{item.title}</Text>
            </View>
          )}
        />

        {tab === 'Animated' ? <AnimatedPanel /> : null}
        {tab === 'Press' ? <PressPanel onLog={setLog} /> : null}

        {/* Floating header: status, style switch, a merging pair */}
        <View style={styles.header} pointerEvents="box-none">
          <GlassView style={styles.status} glassEffectStyle={{ style, animate: true }}>
            <Text style={styles.statusText}>
              liquid glass: {String(isLiquidGlassAvailable())} · api: {String(isGlassEffectAPIAvailable())}
            </Text>
            <Text style={styles.statusText}>{log}</Text>
          </GlassView>
          <GlassContainer spacing={12} style={styles.headerButtons}>
            <GlassButton onPress={() => setStyle((s) => (s === 'regular' ? 'clear' : 'regular'))}>
              <Text style={styles.label}>{style}</Text>
            </GlassButton>
            <GlassButton onPress={() => setStyle((s) => (s === 'none' ? 'regular' : 'none'))}>
              <Text style={styles.label}>{style === 'none' ? 'show' : 'hide'}</Text>
            </GlassButton>
          </GlassContainer>
        </View>

        {/* Glass tab bar */}
        <GlassView style={styles.tabBar} glassEffectStyle={style}>
          {TABS.map((t) => (
            <GlassButton
              key={t}
              glassEffectStyle={t === tab ? 'regular' : 'clear'}
              tintColor={t === tab ? '#0A84FF55' : undefined}
              onPress={() => setTab(t)}
              style={styles.tab}>
              <Text style={styles.label}>{t}</Text>
            </GlassButton>
          ))}
        </GlassView>
        <StatusBar style="auto" />
      </View>
    </GestureHandlerRootView>
  );
}

/** createAnimatedComponent(GlassView) moving and resizing over the list. */
function AnimatedPanel() {
  const t = useSharedValue(0);
  useEffect(() => {
    t.value = withRepeat(withTiming(1, { duration: 2400, easing: Easing.inOut(Easing.quad) }), -1, true);
  }, [t]);
  const animated = useAnimatedStyle(() => ({
    width: 140 + 120 * t.value,
    transform: [{ translateX: -60 + 120 * t.value }, { translateY: 80 * Math.sin(t.value * Math.PI) }],
  }));
  return (
    <View style={styles.center} pointerEvents="none">
      <AnimatedGlassView style={[styles.sheet, animated]}>
        <Text style={styles.label}>Animated</Text>
      </AnimatedGlassView>
    </View>
  );
}

/** Press behaviour with Pressable, GestureDetector, long press and press-then-scroll. */
function PressPanel({ onLog }: { onLog: (s: string) => void }) {
  const tap = Gesture.Tap()
    .runOnJS(true)
    .onEnd(() => onLog('GestureDetector tap'));
  return (
    <View style={styles.center} pointerEvents="box-none">
      <GlassButton onPress={() => onLog('onPress')} onLongPress={() => onLog('onLongPress')}>
        <Text style={styles.label}>Press / long-press</Text>
      </GlassButton>
      <GestureDetector gesture={tap}>
        <GlassView isInteractive style={styles.card}>
          <Text style={styles.label}>GestureDetector</Text>
        </GlassView>
      </GestureDetector>
      <Pressable onPress={() => onLog('Pressable around GlassView')}>
        <GlassView isInteractive style={styles.card} glassEffectStyle="clear" tintColor="#FF375F44">
          <Text style={styles.label}>Pressable + clear + tint</Text>
        </GlassView>
      </Pressable>
      <GlassButton disabled onPress={() => onLog('should not fire')}>
        <Text style={[styles.label, styles.disabled]}>Disabled</Text>
      </GlassButton>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: '#000' },
  list: { paddingTop: 150, paddingBottom: 120 },
  row: { height: 220, marginHorizontal: 12, marginBottom: 12, borderRadius: 20, overflow: 'hidden' },
  photo: { ...StyleSheet.absoluteFill },
  rowTitle: { position: 'absolute', left: 16, bottom: 12, color: '#fff', fontSize: 18, fontWeight: '700' },
  header: { position: 'absolute', top: 56, left: 16, right: 16, gap: 10 },
  status: { borderRadius: 22, paddingHorizontal: 16, paddingVertical: 10 },
  statusText: { fontSize: 12, color: '#111' },
  headerButtons: { flexDirection: 'row', gap: 6 },
  tabBar: {
    position: 'absolute',
    left: 24,
    right: 24,
    bottom: 32,
    height: 64,
    borderRadius: 32,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    paddingHorizontal: 8,
  },
  tab: { paddingHorizontal: 12 },
  label: { fontSize: 16, fontWeight: '600', color: '#111' },
  disabled: { opacity: 0.4 },
  center: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', gap: 16 },
  sheet: { height: 120, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  card: { width: 240, height: 72, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
});
