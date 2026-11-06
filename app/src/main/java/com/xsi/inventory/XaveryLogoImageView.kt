package com.xsi.inventory

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class XaveryLogoImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var baseDrawable: Drawable? = null

    init {
        baseDrawable = drawable
        updateLogoBackground()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        baseDrawable = drawable
        super.setImageDrawable(processDrawable(drawable))
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        baseDrawable = drawable
        super.setImageDrawable(processDrawable(drawable))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateLogoBackground()
    }

    private fun updateLogoBackground() {
        if (baseDrawable != null) {
            super.setImageDrawable(processDrawable(baseDrawable))
        }
    }

    private fun processDrawable(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!isDarkMode) {
            return drawable
        }

        val bitmap = drawableToBitmap(drawable)
        val transparentBitmap = removeWhiteBackground(bitmap)
        return BitmapDrawable(resources, transparentBitmap)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun removeWhiteBackground(srcBitmap: Bitmap): Bitmap {
        val width = srcBitmap.width
        val height = srcBitmap.height
        val mutableBitmap = srcBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            // If the pixel is white or near-white, make it transparent
            if (red > 220 && green > 220 && blue > 220) {
                pixels[i] = 0x00000000 // Transparent
            }
        }

        mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return mutableBitmap
    }
}
