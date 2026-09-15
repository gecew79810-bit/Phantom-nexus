package com.example

import android.app.Application
import android.util.Log

class MaxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
            Log.i("MaxApplication", "SQLCipher native library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("MaxApplication", "Failed to load sqlcipher native library: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("MaxApplication", "Unexpected exception loading sqlcipher: ${e.message}", e)
        }
    }
}
