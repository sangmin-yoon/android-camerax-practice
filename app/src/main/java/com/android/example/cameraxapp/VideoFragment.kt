package com.android.example.cameraxapp

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.android.example.cameraxapp.databinding.FragmentVideoBinding
import java.lang.Exception
import java.util.*
import java.util.concurrent.ExecutorService

class VideoFragment : Fragment() {

    private lateinit var binding: FragmentVideoBinding
    private var videoCapture : VideoCapture<Recorder>? = null
    private var recording: Recording? = null
//    private lateinit var cameraExecutor = ExecutorService


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) {
            if (allPermissionsGranted()) {
                startCamera()
                Log.d(TAG, "카메라시작")
            } else {
                Toast.makeText(context, "권한 승인을 하지 않았습니다.", Toast.LENGTH_SHORT).show()
                fromFragmentFinish()
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
            Log.d(TAG, "카메라시작")
        } else {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        binding.videoCaptureButton.setOnClickListener { captureVideo() }

    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun fromFragmentFinish() {
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }


            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA


            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview
                )
            } catch (exc: Exception) {
                Log.e(TAG, "라이프사이클 바인딩에 실패하였습니다", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))

        videoCapture = VideoCapture.withOutput(Recorder.Builder().build())

    }

    private fun captureVideo() {
        Log.d(TAG, "클릭됨")


        videoCapture = this.videoCapture ?: throw IllegalStateException("카메라 초기화 실패")
        Log.d(TAG, "비디오캡쳐 생성됨")

        binding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        val name = java.text.SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())
        Log.d(TAG, "name: ${name} 입니다")

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }


        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(requireContext().contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture!!.output
            .prepareRecording(requireContext(),mediaStoreOutputOptions)
            .apply {
                if(PermissionChecker.checkSelfPermission(requireContext(),Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }.start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.videoCaptureButton.apply {
                            Log.d(TAG,"촬영시작")
                            isEnabled = true
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        if(!recordEvent.hasError()) {
                            val msg = "비디오 촬영 성공!//${recordEvent.outputResults.outputUri}"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG,msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG,"비디오 촬영 성공?//${recordEvent.outputResults.outputUri}")
                        }
                        binding.videoCaptureButton.apply {
                            Log.d(TAG, "촬영종료")
                            isEnabled = true
                        }
                    }
                }
            }

    }


    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-hh-ss-SSS"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        ).toTypedArray()
    }

}
