package org.tensorflow.lite.examples.imageclassification.fragments

import android.graphics.Bitmap

class BitmapUpdaterApi {
    var latestBitmap : Bitmap? = null
    var latestImageRotation: Int = 0
}