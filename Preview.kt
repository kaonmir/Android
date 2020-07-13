package com.example.wordbook

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Arrays.asList
import java.util.concurrent.Semaphore


open class Preview(context: Context, textureView: TextureView) : Thread() {
    private val TAG = "Preview"

    private var mPreviewSize: Size? = null
    private var mContext: Context = context
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var mTextureView: TextureView = textureView

    private fun getBackFacingCameraId(cManager: CameraManager): String? {
        try {
            for(cameraId in cManager.cameraIdList) {
                val characteristics = cManager.getCameraCharacteristics(cameraId)
                val cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING)
                if(cOrientation == CameraCharacteristics.LENS_FACING_BACK) return cameraId
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return null
    }

    fun openCamera() {
        val manager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.d(TAG, "Open Camera")
        try {
            val cameraId = getBackFacingCameraId(manager) as String
            val characteristics  = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mPreviewSize = map?.getOutputSizes(SurfaceTexture::class.java)?.get(0)

            val permissionCamera = ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
            if(permissionCamera == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(
                    mContext as Activity,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
            } else {
                manager.openCamera(cameraId, mStateCallback, null)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.d(TAG, "openCamera X")
    }

    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "onOpened")
            mCameraDevice = camera
            startPreview()
        }
        override fun onDisconnected(p0: CameraDevice) {
            Log.d(TAG, "onDisconnected")
        }
        override fun onError(p0: CameraDevice, p1: Int) {
            Log.d(TAG, "onError")
        }
    }

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(p0: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureAvailable, width=$width, height=$height")
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
        }
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
        }
        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
            return true
        }
    }

    protected fun startPreview() {
        if(null == mCameraDevice || !mTextureView.isAvailable || null == mPreviewSize) {
            Log.e(TAG, "startPreview fail - mCameraDevice = ${mCameraDevice == null}, " +
                            "mTextureView.isAvailable = ${!mTextureView.isAvailable}, " +
                            "mPreviewSize = ${mPreviewSize == null}");
            return
        }

        val texture = mTextureView.surfaceTexture
        if(null == texture) {
            Log.e(TAG, "texture is null, return")
            return
        }

        texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
        val surface = Surface(texture)

        try {
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        mPreviewBuilder!!.addTarget(surface)

        try {
            mCameraDevice!!.createCaptureSession(asList(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(mContext, "onConfigureFailed", Toast.LENGTH_LONG).show()
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    mPreviewSession = session
                    updatePreview()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    protected fun updatePreview() {
        if (null == mCameraDevice) {
            Log.d(TAG, "updatePreview error, return")
            return
        }

        mPreviewBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        val thread = HandlerThread("CameraPreview")
        thread.start()
        val backgroundHandler = Handler(thread.looper)

        try {
            mPreviewSession!!.setRepeatingRequest(mPreviewBuilder!!.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun setSurfaceTextureListener() {
        mTextureView.surfaceTextureListener = mSurfaceTextureListener
    }

    fun onResume() {
        Log.d(TAG, "onResume")
        setSurfaceTextureListener()
    }

    private val mCameraOpenCloseLock = Semaphore(1)
    fun onPause() {
        Log.d(TAG, "onPause")
        try {
            mCameraOpenCloseLock.acquire()
            if (null!=mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
                Log.d(TAG, "CameraDevice Close")
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing")
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    init {
        Log.i(TAG, "mTextureView.isAvailable: ${mTextureView.isAvailable}")
    }

}