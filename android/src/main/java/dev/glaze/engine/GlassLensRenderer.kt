/*
 * Vendored from QWEA0/Liquid-Glass-Android v2.0.11 (MIT, see THIRD_PARTY_NOTICES.md).
 *
 * Changes from upstream: package renamed; the backdrop is recorded through a caller-supplied
 * lambda (react-native-glaze's GlazeCapture) instead of upstream's BackdropCapture; recording is
 * split from drawing so the host can re-record in onPreDraw without invalidating itself; the
 * outward-refraction mode and its compositing layer are removed. The AGSL shader is unchanged.
 */
package dev.glaze.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassLensRenderer {

    companion object {
        private const val TAG = "GlazeLens"

        /** Compiles the shader once to confirm the driver accepts it. */
        fun probe(): Boolean = try {
            RuntimeShader(LENS_AGSL)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Lens AGSL compile failed; using blur tier", e)
            false
        }

        private const val LENS_AGSL = """
            uniform shader content;
            uniform float  margin;
            uniform float2 viewSize;
            uniform float4 shape1;    // 真实主形状（触摸凸起的尺度用它）
            uniform float4 radii1;
            uniform float4 shape1L;   // 主形状：平边方向延伸到视图外，那条边就没有斜面（视图矩形外硬切）
            uniform float  rimSoft;   // 边缘柔化：折射带内沿法线方向的抹匀宽度（px，0 = 关）
            uniform float4 shape2;
            uniform float  radius2;
            uniform float  blendK;
            uniform float  bevel;
            uniform float  refractPx;
            uniform float  falloff;      // 折射剖面：> 0 为逆幂（引力透镜）衰减指数，0 = 平方斜面
            uniform float  refractDir;   // -1 向内采样（默认，与 iOS 一致：内侧压缩镜像）/ +1 向外（可选的凸透镜模式）
            uniform float2 sampleLo;     // 采样安全区（录制内容坐标）：区外没有内容，读到的是透明黑
            uniform float2 sampleHi;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  rimBandMax;   // 贴边高光带宽度上限（px，小控件按短边收）
            uniform float4 tintColor;
            uniform float  adaptiveTint;
            uniform float4 glassTint;
            uniform float  dimAmount;
            uniform float  satFactor;
            uniform float  press;
            uniform float2 touchPos;
            uniform float  touchAmp;

            float sdRoundedBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
            }

            // 逐角半径版：r = (左上, 右上, 右下, 左下)，按 p 所在象限选半径
            float sdRoundedBox4(float2 p, float2 b, float4 r) {
                float rx = (p.x > 0.0) ? ((p.y > 0.0) ? r.z : r.y) : ((p.y > 0.0) ? r.w : r.x);
                float2 q = abs(p) - b + rx;
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rx;
            }

            float sminPoly(float a, float b, float k) {
                float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            // 斜面 / 法线 / 高光用透镜形状（平边已延伸出去，不产生边缘）
            float lensSDF(float2 p) {
                float d = sdRoundedBox4(p - shape1L.xy, shape1L.zw, radii1);
                if (shape2.z > 0.5) {
                    float d2 = sdRoundedBox(p - shape2.xy, shape2.zw, radius2);
                    if (blendK > 0.5) {
                        d = sminPoly(d, d2, blendK);
                    } else {
                        d = min(d, d2);
                    }
                }
                return d;
            }

            half4 main(float2 coord) {
                float2 p = coord - float2(margin, margin);
                // 视图矩形外硬切：平边方向形状延伸到了视图外，靠这一刀收口，
                // 平边上不做羽化，两块玻璃贴边拼接时不会叠出一条发丝缝
                if (p.x < 0.0 || p.y < 0.0 || p.x > viewSize.x || p.y > viewSize.y) {
                    return half4(0.0);
                }
                float d = lensSDF(p);

                // 覆盖率：1.5px 抗锯齿羽化，形状外完全透明
                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                // SDF 数值梯度 → 屏幕空间外法线
                float2 n = float2(
                    lensSDF(p + float2(1.0, 0.0)) - lensSDF(p - float2(1.0, 0.0)),
                    lensSDF(p + float2(0.0, 1.0)) - lensSDF(p - float2(0.0, 1.0))
                );
                float nLen = length(n);
                if (nLen > 0.0001) {
                    n = n / nLen;
                } else {
                    n = float2(0.0, -1.0);
                }

                // 厚度剖面：t=1 平坦内部，t=0 边缘；slope 为斜面陡峭度（折射位移的比例）
                float t = clamp(-d / max(bevel, 1.0), 0.0, 1.0);
                float edge = 1.0 - t;
                float slope;
                if (falloff > 0.001) {
                    // 引力透镜式逆幂衰减：位移 ∝ (1 + x/k)^-p，核半径 k = 斜面宽度的 1/4，
                    // 减去带末端的值再归一化，贴边 = 1、带末端平滑落到 0。越贴边越剧烈：
                    // p = 2 时离边 k 处只剩 1/4，绝大部分弯折压在最外几个像素，
                    // 内侧只留一段缓慢回落的轻微放大尾巴
                    float gB = pow(5.0, -falloff);
                    slope = (pow(1.0 + 4.0 * t, -falloff) - gB) / (1.0 - gB);
                } else {
                    // 平方斜面：弯折沿整条斜面带均匀铺开
                    slope = edge * edge;
                }

                // 折射：refractDir = -1 沿法线向内采样（默认，与 iOS 一致）——边缘是内侧背景的
                // 压缩镜像；+1 为可选的凸透镜模式，向外采样，形状外的背景被弯进边缘，
                // 靠近的内容还没进到玻璃下面就先出现在边缘，进来之后沿边缘延展。
                // RuntimeShader 子输入只保证"输出裁剪区"内可采样（Android 未暴露 Skia 的
                // childSampleRadius），向外采样必须让输出区覆盖整个外扩录制区——见 draw()
                // 里的外层合成节点；采样再由 sampleLo/Hi 钳在有内容的范围内
                float refr = refractPx * (1.0 + 0.6 * press);
                float2 offset = n * (refractDir * slope * refr);

                // 触摸局部液态凸起（手指下方的局部放大：采样向触点收缩）
                if (touchAmp > 0.001) {
                    float2 tp = p - touchPos;
                    float tr = length(tp);
                    float sigma = max(max(shape1.z, shape1.w), 1.0);
                    float bump = touchAmp * exp(-(tr * tr) / (sigma * sigma * 0.30));
                    if (tr > 1.0) {
                        offset -= (tp / tr) * (bump * refr * 0.5);
                    }
                }

                // 色散：三通道折射量不同（蓝光弯折最多），边缘出现光谱边纹
                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                // 安全钳制到有内容的采样区，杜绝透明黑
                float2 lo = sampleLo;
                float2 hi = sampleHi;
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);
                float3 col;
                if (rimSoft > 0.01 && slope > 0.001) {
                    // 边缘柔化：折射带内沿法线方向抹匀（宽度随斜面深度增长），
                    // 压缩带从一条硬线变成一段渐变，采样点仍钳在内容区内
                    float2 sm = n * (rimSoft * slope);
                    float2 cR1 = clamp(cR - sm, lo, hi);
                    float2 cR2 = clamp(cR + sm, lo, hi);
                    float2 cG1 = clamp(cG - sm, lo, hi);
                    float2 cG2 = clamp(cG + sm, lo, hi);
                    float2 cB1 = clamp(cB - sm, lo, hi);
                    float2 cB2 = clamp(cB + sm, lo, hi);
                    col = float3(
                        (content.eval(cR).r + content.eval(cR1).r + content.eval(cR2).r) / 3.0,
                        (content.eval(cG).g + content.eval(cG1).g + content.eval(cG2).g) / 3.0,
                        (content.eval(cB).b + content.eval(cB1).b + content.eval(cB2).b) / 3.0
                    );
                } else {
                    col = float3(
                        content.eval(cR).r,
                        content.eval(cG).g,
                        content.eval(cB).b
                    );
                }

                // 饱和度（合并进同一 pass）；提饱和端走 vibrancy 曲线：低饱和
                // 像素多提、高饱和像素少提、极亮像素保护，避免线性提饱和把浓色
                // 推过曝（曲线与 GlassRuntimeEffects 的 API 36 滤镜一致）
                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                if (satFactor <= 1.0) {
                    col = mix(float3(lum), col, satFactor);
                } else {
                    float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                    float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                    float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                    float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                    col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));
                }

                // 自适应染色（Regular）/ 压暗层（Clear）
                if (adaptiveTint > 0.5) {
                    // 逐像素自适应：按局部（模糊后）亮度在提亮/压暗之间平滑过渡，
                    // 玻璃跨明暗背景时不再整体翻转；曲线与全局版本
                    // （LiquidGlassView.onLuminanceSample）一致
                    float lumT = dot(col, float3(0.2126, 0.7152, 0.0722));
                    float e = smoothstep(0.35, 0.75, lumT);
                    col = mix(col, float3(1.0 - e), 0.14 + 0.08 * e);
                } else {
                    col = mix(col, tintColor.rgb, tintColor.a);
                }
                col = col * (1.0 - dimAmount);

                // 使用方指定的玻璃本体色：按"有色介质"建模——吸收（保留背景明暗
                // 层次与折射细节）+ 少量散射（暗背景下也看得出色相）。
                // 位置在光照之前：染色属于透射，镜面高光属于表面反射，不该被染色
                if (glassTint.a > 0.002) {
                    float lumTint = dot(col, float3(0.2126, 0.7152, 0.0722));
                    float3 absorbed = col * mix(float3(1.0), glassTint.rgb, 0.85);
                    float3 scattered = glassTint.rgb * (0.38 * (1.0 - lumTint));
                    col = mix(col, clamp(absorbed + scattered, float3(0.0), float3(1.0)), glassTint.a);
                }

                // 光照：同一法线场驱动。整圈亮边的明暗只由 dot(N, -L) 决定，没有与方向
                // 无关的常亮项——侧向（法线垂直于光线处）归零，不会留下一圈固定描边。
                // 两道对称的角度瓣（沿 iOS 26 控制中心截图里的控件一圈逐角量得）：迎光侧
                // 与背光侧峰值相等（背光侧是透明介质的内壁反射），瓣宽 pow 4.5（离轴 30°
                // 剩一半、45° 归零）；迎光侧另加一层向内的柔和辉光——iOS 左上角是亮线 +
                // 辉光，右下角只有亮线，两侧的边缘内侧都没有暗带
                float facing = dot(n, -lightDir);
                float lobeF = pow(max(facing, 0.0), 4.5);
                float lobeB = pow(max(-facing, 0.0), 4.5);

                // 贴边亮线（中心在边内 1px，半宽 2px）+ 迎光侧辉光：从亮线内侧（边内 3px）起
                // 向内 1.5 次幂衰减，宽度与斜面弱相关、上限默认 6px、小控件按短边收。
                // 辉光不叠在亮线上，两侧亮线峰值相等
                float bandW = clamp(bevel * 0.3, 2.0, rimBandMax);
                float glowIn = clamp((-d - 1.0) / 2.0, 0.0, 1.0);
                float glow = glowIn * pow(clamp(1.0 - (-d - 3.0) / bandW, 0.0, 1.0), 1.5) * cov;
                float hair = clamp(1.0 - abs(d + 1.0) / 2.0, 0.0, 1.0) * cov;
                float spec = (hair * 0.70 * (lobeF + lobeB) + glow * 0.10 * lobeF)
                             * specStrength * (1.0 - 0.35 * press);
                col += float3(spec);

                col = clamp(col, float3(0.0), float3(1.0));
                return half4(half3(col * cov), half(cov));
            }
        """
    }

    /** Lens parameters. Value semantics: the RenderEffect is rebuilt only when they change. */
    data class LensParams(
        val blurRadius: Float,
        val shape1CX: Float, val shape1CY: Float,
        val shape1HW: Float, val shape1HH: Float,
        val radius1TL: Float, val radius1TR: Float, val radius1BR: Float, val radius1BL: Float,
        val rimSoft: Float,
        val shape2CX: Float, val shape2CY: Float,
        val shape2HW: Float, val shape2HH: Float, val radius2: Float,
        val blendK: Float,
        val bevel: Float,
        val refract: Float,
        val falloff: Float,
        val rimBandMax: Float,
        val dispersion: Float,
        val lightX: Float, val lightY: Float,
        val spec: Float,
        val tint: Int,
        val adaptiveTint: Boolean,
        val glassTint: Int,
        val dim: Float,
        val saturation: Float,
        val press: Float,
        val touchX: Float, val touchY: Float, val touchAmp: Float
    )

    private val renderNode = RenderNode("GlazeLens")

    private var shader: RuntimeShader? = null
    private var shaderBroken = false

    private var lastParams: LensParams? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastMargin = -1

    val isAvailable: Boolean
        get() = !shaderBroken

    /**
     * Updates the effect and re-records the backdrop into the lens node.
     *
     * [drawBackdrop] receives a canvas whose origin is the backdrop source's top-left.
     * [offsetX]/[offsetY] are the glass position relative to that source.
     */
    fun record(
        width: Int,
        height: Int,
        margin: Int,
        params: LensParams,
        offsetX: Float,
        offsetY: Float,
        drawBackdrop: (Canvas) -> Unit
    ): Boolean {
        if (shaderBroken || width <= 0 || height <= 0) return false
        val sh = shader ?: try {
            RuntimeShader(LENS_AGSL).also { shader = it }
        } catch (e: Exception) {
            Log.e(TAG, "Lens AGSL compile failed, falling back", e)
            shaderBroken = true
            return false
        }

        if (params != lastParams || width != lastWidth || height != lastHeight || margin != lastMargin) {
            try {
                renderNode.setRenderEffect(buildEffect(sh, params, margin, width, height))
            } catch (e: Exception) {
                Log.e(TAG, "Lens effect build failed, falling back", e)
                shaderBroken = true
                return false
            }
            lastParams = params
            lastWidth = width
            lastHeight = height
            lastMargin = margin
        }

        // Record with a margin so the blur and refraction sample real content at the edges
        val recW = width + 2 * margin
        val recH = height + 2 * margin
        renderNode.setPosition(-margin, -margin, width + margin, height + margin)
        val c = renderNode.beginRecording(recW, recH)
        try {
            c.translate(margin - offsetX, margin - offsetY)
            drawBackdrop(c)
        } finally {
            renderNode.endRecording()
        }
        return true
    }

    fun draw(canvas: Canvas) {
        if (!shaderBroken && renderNode.hasDisplayList()) canvas.drawRenderNode(renderNode)
    }

    fun setAlpha(alpha: Float) {
        renderNode.setAlpha(alpha)
    }

    private fun buildEffect(
        sh: RuntimeShader,
        p: LensParams,
        margin: Int,
        width: Int,
        height: Int
    ): RenderEffect {
        sh.setFloatUniform("margin", margin.toFloat())
        sh.setFloatUniform("viewSize", width.toFloat(), height.toFloat())
        sh.setFloatUniform("shape1", p.shape1CX, p.shape1CY, p.shape1HW, p.shape1HH)
        sh.setFloatUniform("radii1", p.radius1TL, p.radius1TR, p.radius1BR, p.radius1BL)
        // No flat edges: the lens shape is the real shape
        sh.setFloatUniform("shape1L", p.shape1CX, p.shape1CY, p.shape1HW, p.shape1HH)
        sh.setFloatUniform("rimSoft", p.rimSoft)
        sh.setFloatUniform("shape2", p.shape2CX, p.shape2CY, p.shape2HW, p.shape2HH)
        sh.setFloatUniform("radius2", p.radius2)
        sh.setFloatUniform("blendK", p.blendK)
        sh.setFloatUniform("bevel", p.bevel)
        sh.setFloatUniform("refractPx", p.refract)
        sh.setFloatUniform("falloff", p.falloff)
        sh.setFloatUniform("refractDir", -1f)
        // Inward sampling: the view rect (inside the recorded margin) always has content
        sh.setFloatUniform("sampleLo", margin + 1f, margin + 1f)
        sh.setFloatUniform("sampleHi", margin + width - 1f, margin + height - 1f)
        sh.setFloatUniform("dispersion", p.dispersion)
        sh.setFloatUniform("lightDir", p.lightX, p.lightY)
        sh.setFloatUniform("specStrength", p.spec)
        sh.setFloatUniform("rimBandMax", p.rimBandMax)
        sh.setFloatUniform(
            "tintColor",
            Color.red(p.tint) / 255f,
            Color.green(p.tint) / 255f,
            Color.blue(p.tint) / 255f,
            Color.alpha(p.tint) / 255f
        )
        sh.setFloatUniform("adaptiveTint", if (p.adaptiveTint) 1f else 0f)
        sh.setFloatUniform(
            "glassTint",
            Color.red(p.glassTint) / 255f,
            Color.green(p.glassTint) / 255f,
            Color.blue(p.glassTint) / 255f,
            Color.alpha(p.glassTint) / 255f
        )
        sh.setFloatUniform("dimAmount", p.dim)
        sh.setFloatUniform("satFactor", p.saturation / 100f)
        sh.setFloatUniform("press", p.press)
        sh.setFloatUniform("touchPos", p.touchX, p.touchY)
        sh.setFloatUniform("touchAmp", p.touchAmp)

        val lens = RenderEffect.createRuntimeShaderEffect(sh, "content")
        return if (p.blurRadius > 0.01f) {
            RenderEffect.createChainEffect(
                lens,
                RenderEffect.createBlurEffect(p.blurRadius, p.blurRadius, Shader.TileMode.CLAMP)
            )
        } else {
            lens
        }
    }

    fun release() {
        renderNode.discardDisplayList()
        shader = null
        lastParams = null
    }
}
