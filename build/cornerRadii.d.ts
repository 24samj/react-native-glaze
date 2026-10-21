import { type StyleProp, type ViewStyle } from 'react-native';
/**
 * Corner radii `[topLeft, topRight, bottomRight, bottomLeft]` in dp, read from `style`
 * with the same precedence as expo-glass-effect on iOS (LTR). Percentages and animated
 * values are not supported: the radius is read once per render.
 */
export declare function cornerRadiiFrom(style: StyleProp<ViewStyle>): [number, number, number, number];
//# sourceMappingURL=cornerRadii.d.ts.map