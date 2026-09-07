package com.riftcompanion.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RiftCompanionApp : Application() {

    @Inject
    lateinit var imageLoader: coil3.ImageLoader

    override fun onCreate() {
        super.onCreate()
        CoilLoaderHolder.imageLoader = imageLoader
    }
}

object CoilLoaderHolder {
    @Volatile
    var imageLoader: coil3.ImageLoader? = null
}
