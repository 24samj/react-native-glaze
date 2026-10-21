import { requireNativeModule } from 'expo';
const Glaze = requireNativeModule('Glaze');
/** `true` when the full lens tier is active (API 33+, not demoted). */
export function isLiquidGlassAvailable() {
    return Glaze.isLiquidGlassAvailable();
}
/** Always `true` on Android: some glass tier always renders. */
export function isGlassEffectAPIAvailable() {
    return true;
}
//# sourceMappingURL=availability.android.js.map