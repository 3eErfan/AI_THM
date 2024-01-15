package org.tensorflow.lite.examples.imageclassification.fragments

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class DynamicBitmapSource(private val bitmapUpdaterApi: BitmapUpdaterApi) {
    private var run = false
    lateinit var bitmapStream: Flow<BitmapUpdaterApi?>

    private fun runStream() {
        bitmapStream = flow {
            while (run) {
                emit(bitmapUpdaterApi);
            }
        }.flowOn(Dispatchers.Default)
    }

    fun startStream() {
        run = true
        runStream()
    }

    fun pauseStream() {
        run = false
    }
}