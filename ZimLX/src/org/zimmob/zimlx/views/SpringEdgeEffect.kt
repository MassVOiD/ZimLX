/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.zimmob.zimlx.views

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.touch.OverScroll
import org.zimmob.zimlx.KFloatProperty
import org.zimmob.zimlx.KFloatPropertyCompat
import org.zimmob.zimlx.zimPrefs
import kotlin.reflect.KMutableProperty0

class SpringEdgeEffect(
        context: Context,
        private val getMax: () -> Int,
        private val target: KMutableProperty0<Float>,
        private val activeEdge: KMutableProperty0<SpringEdgeEffect?>,
        private val velocityMultiplier: Float) : EdgeEffect(context) {

    private val prefs = context.zimPrefs

    private val shiftProperty = KFloatProperty(target, "value")
    private val spring = SpringAnimation(this, KFloatPropertyCompat(target, "value"), 0f).apply {
        spring = SpringForce(0f).setStiffness(850f).setDampingRatio(0.5f)
    }
    private var distance = 0f

    override fun draw(canvas: Canvas) = false

    override fun onAbsorb(velocity: Int) {
        releaseSpring(velocityMultiplier * velocity)
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        activeEdge.set(this)
        distance += deltaDistance * (velocityMultiplier * 2)
        target.set(OverScroll.dampedScroll(distance * getMax(), getMax()).toFloat())
    }

    override fun onRelease() {
        distance = 0f
        releaseSpring(0f)
    }

    private fun releaseSpring(velocity: Float) {
        if (prefs.enablePhysics) {
            spring.setStartVelocity(velocity)
            spring.setStartValue(target.get())
            spring.start()
        } else {
            ObjectAnimator.ofFloat(this, shiftProperty, 0f)
                    .setDuration(100)
                    .start()
        }
    }

    class Manager(val view: View) {

        var shiftX = 0f
            set(value) {
                if (field != value) {
                    field = value
                    view.invalidate()
                }
            }
        var shiftY = 0f
            set(value) {
                if (field != value) {
                    field = value
                    view.invalidate()
                }
            }

        var activeEdgeX: SpringEdgeEffect? = null
            set(value) {
                if (field != value) {
                    field?.run { value?.distance = distance }
                }
                field = value
            }
        var activeEdgeY: SpringEdgeEffect? = null
            set(value) {
                if (field != value) {
                    field?.run { value?.distance = distance }
                }
                field = value
            }

        inline fun withSpring(canvas: Canvas, allow: Boolean = true, body: () -> Boolean): Boolean {
            val result: Boolean
            if ((shiftX == 0f && shiftY == 0f) || !allow) {
                result = body()
            } else {
                canvas.translate(shiftX, shiftY)
                result = body()
                canvas.translate(-shiftX, -shiftY)
            }
            return result
        }

        inline fun withSpringNegative(canvas: Canvas, allow: Boolean = true, body: () -> Boolean): Boolean {
            val result: Boolean
            if ((shiftX == 0f && shiftY == 0f) || !allow) {
                result = body()
            } else {
                canvas.translate(-shiftX, -shiftY)
                result = body()
                canvas.translate(shiftX, shiftY)
            }
            return result
        }

        fun createFactory() = SpringEdgeEffectFactory()

        inner class SpringEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

            override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
                return when (direction) {
                    DIRECTION_LEFT -> SpringEdgeEffect(view.context, view::getWidth, ::shiftX, ::activeEdgeX, 0.3f)
                    DIRECTION_TOP -> SpringEdgeEffect(view.context, view::getHeight, ::shiftY, ::activeEdgeY, 0.3f)
                    DIRECTION_RIGHT -> SpringEdgeEffect(view.context, view::getWidth, ::shiftX, ::activeEdgeX, -0.3f)
                    DIRECTION_BOTTOM -> SpringEdgeEffect(view.context, view::getWidth, ::shiftY, ::activeEdgeY, -0.3f)
                    else -> super.createEdgeEffect(recyclerView, direction)
                }
            }
        }
    }
}