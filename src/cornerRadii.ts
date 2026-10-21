import { StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

function num(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined;
}

/**
 * Corner radii `[topLeft, topRight, bottomRight, bottomLeft]` in dp, read from `style`
 * with the same precedence as expo-glass-effect on iOS (LTR). Percentages and animated
 * values are not supported: the radius is read once per render.
 */
export function cornerRadiiFrom(style: StyleProp<ViewStyle>): [number, number, number, number] {
  const s = StyleSheet.flatten(style) ?? {};
  const r = num(s.borderRadius) ?? 0;
  return [
    num(s.borderTopLeftRadius) ?? num(s.borderTopStartRadius) ?? num(s.borderStartStartRadius) ?? r,
    num(s.borderTopRightRadius) ?? num(s.borderTopEndRadius) ?? num(s.borderStartEndRadius) ?? r,
    num(s.borderBottomRightRadius) ??
      num(s.borderBottomEndRadius) ??
      num(s.borderEndEndRadius) ??
      r,
    num(s.borderBottomLeftRadius) ??
      num(s.borderBottomStartRadius) ??
      num(s.borderEndStartRadius) ??
      r,
  ];
}
