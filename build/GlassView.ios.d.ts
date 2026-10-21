import type { GlassViewProps } from './types';
/**
 * iOS 26+: Apple's Liquid Glass via expo-glass-effect, props forwarded untouched.
 * Below iOS 26, or when the app opts out of Liquid Glass: a system blur material, so the UI
 * still reads as glass instead of expo-glass-effect's plain View.
 */
export default function GlassView(props: GlassViewProps): import("react").JSX.Element;
//# sourceMappingURL=GlassView.ios.d.ts.map