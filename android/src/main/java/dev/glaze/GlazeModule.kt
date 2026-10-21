package dev.glaze

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class GlazeModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("Glaze")

        Function("isLiquidGlassAvailable") {
            val context = appContext.reactContext ?: return@Function false
            GlazeTiers.isLensActive(context)
        }

        View(GlazeView::class) {
            Name("GlazeView")

            Prop("glassStyle") { view: GlazeView, value: String? ->
                view.setGlassStyle(value ?: "regular")
            }
            Prop("styleAnimation") { view: GlazeView, value: List<Double>? ->
                // [animate (0|1), durationSeconds (-1 = default)]
                val animate = (value?.getOrNull(0) ?: 0.0) > 0.5
                val duration = value?.getOrNull(1)?.takeIf { it >= 0 }
                view.setStyleAnimation(animate, duration)
            }
            Prop("glassTint") { view: GlazeView, value: Double? ->
                view.setGlassTint(value?.toLong()?.toInt() ?: 0)
            }
            Prop("interactive") { view: GlazeView, value: Boolean? ->
                view.setInteractive(value ?: false)
            }
            Prop("glassColorScheme") { view: GlazeView, value: String? ->
                view.setColorScheme(value ?: "auto")
            }
            Prop("cornerRadii") { view: GlazeView, value: List<Double>? ->
                view.setCornerRadii(value)
            }

            OnViewDidUpdateProps { view: GlazeView ->
                view.commitProps()
            }
        }

        View(GlazeContainerView::class) {
            Name("GlazeContainerView")

            Prop("spacing") { view: GlazeContainerView, value: Double? ->
                view.setSpacing(value)
            }
        }
    }
}
