/*
 * Copyright (c) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.apps.watchme;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
//import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.apps.watchme.util.Utils;
import com.google.android.apps.watchme.util.YouTubeApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         StreamerActivity class which previews the camera and streams via StreamerService.
 */
public class StreamerActivity extends Activity {
    // CONSTANTS
    // TODO: Stop hardcoding this and read values from the camera's supported sizes.
    public static final int CAMERA_WIDTH = 1600;
    public static final int CAMERA_HEIGHT = 1200;
    private static final int REQUEST_CAMERA_MICROPHONE = 0;

    // Member variables
    private StreamerService streamerService;
    private PowerManager.WakeLock wakeLock;
    //    private Preview preview;
    private String rtmpUrl;
    private TextureView textureView;
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //@edsonAndrade
            // Notification when TextView is available
            //Toast.makeText(getApplicationContext(),"TextureView is available", Toast.LENGTH_SHORT).show();
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
            //@edsonAndrade
            // notification when camera is connetected
            //Toast.makeText(getApplicationContext(), "The Camera is now connected.", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            //@edsonAndrade
            //This is to clean up the resources when not in use
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            //@edsonAndrade
            //This is to clean up the resources when not in use
            camera.close();
            cameraDevice = null;
        }
    };

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private String cameraId;
    private Size previewSize;

    private CaptureRequest.Builder captureRequestBuilder;

    private ImageButton recordImageButtton;
    //@edsonAndrade
    // The camera when starting will automatically record from the start just like it did before
    private boolean isRecording = true;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);
    }



    public void closeCamera(){
        if(cameraDevice != null){
            //@edsonAndrade
            //This is to clean up the resources when not in use
            cameraDevice.close();
            cameraDevice = null;
        }
    }
    @Override
    protected void onResume() {
        Log.d(MainActivity.APP_NAME, "onResume");

        super.onResume();
        //@edsonAndrade
        startBackgroundThread();

        // @edsonAndrade
        // Checking if the textView is available
        if(textureView.isAvailable()){
            setupCamera(textureView.getWidth(),textureView.getHeight());
            connectCamera();
        }else{
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }

        if (streamerService != null) {
            restoreStateFromService();
        }
    }
    @Override
    protected void onPause() {
        Log.d(MainActivity.APP_NAME, "onPause");

        if (streamerService != null) {
            streamerService.releaseCamera();
        }

        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void setupCamera(int width, int height){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraIDs : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIDs);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)== CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //@edsonAndrade
                // Alike the camera api version of the app, I am forcing the app into the landscape mode
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                int totalRotation = sensorToDeeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if(swapRotation){
                    rotatedHeight = width;
                    rotatedWidth = height;
                }
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth, rotatedHeight);
                cameraId = cameraIDs;
                return;
            }
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void connectCamera(){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    void startPreview(){
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try{
                                session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            }catch (CameraAccessException e){
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(),"The preview failed to setup", Toast.LENGTH_SHORT).show();
                        }
                    }, null);

        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }


    private ServiceConnection streamerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(MainActivity.APP_NAME, "onServiceConnected");

            streamerService = ((StreamerService.LocalBinder) service).getService();

            restoreStateFromService();
            startStreaming();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.e(MainActivity.APP_NAME, "onServiceDisconnected");

            // This should never happen, because our service runs in the same process.
            streamerService = null;
        }
    };
    private String broadcastId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(MainActivity.APP_NAME, "onCreate");
        super.onCreate(savedInstanceState);

        broadcastId = getIntent().getStringExtra(YouTubeApi.BROADCAST_ID_KEY);
        //Log.v(MainActivity.APP_NAME, broadcastId);

        rtmpUrl = getIntent().getStringExtra(YouTubeApi.RTMP_URL_KEY);

        if (rtmpUrl == null) {
            Log.w(MainActivity.APP_NAME, "No RTMP URL was passed in; bailing.");
            finish();
        }
        Log.i(MainActivity.APP_NAME, String.format("Got RTMP URL '%s' from calling activity.", rtmpUrl));

        setContentView(R.layout.streamer);
        // @edsonAndrade
        //Setting the textview as the main video display for camera2 api
        textureView = (TextureView) findViewById(R.id.surfaceViewPreview);
        //preview = (Preview) findViewById(R.id.surfaceViewPreview);

        if (!bindService(new Intent(this, StreamerService.class), streamerConnection,
                BIND_AUTO_CREATE | BIND_DEBUG_UNBIND)) {
            Log.e(MainActivity.APP_NAME, "Failed to bind StreamerService!");
        }

        recordImageButtton = (ImageButton) findViewById(R.id.toggleBroadcasting);
        recordImageButtton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(isRecording){
                    isRecording = false;
                    //recordImageButtton.setImageResource(R.mipmap.);
                }
            }
        });

        final ToggleButton switchCamera = (ToggleButton) findViewById(R.id.button);
        switchCamera.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (switchCamera.isChecked()) {
//                    streamerService.startStreaming(rtmpUrl);
               } else {
//                    streamerService.stopStreaming();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.d(MainActivity.APP_NAME, "onDestroy");

        super.onDestroy();

        if (streamerConnection != null) {
            unbindService(streamerConnection);
        }

        stopStreaming();

        if (streamerService != null) {
            streamerService.releaseCamera();
        }
    }

    private void restoreStateFromService() {
//        preview.setCamera(Utils.getCamera(Camera.CameraInfo.CAMERA_FACING_FRONT));
    }

    private void startStreaming() {
        Log.d(MainActivity.APP_NAME, "startStreaming");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, this.getClass().getName());
        wakeLock.acquire();

        if (!streamerService.isStreaming()) {

            String cameraPermission = Manifest.permission.CAMERA;
            String microphonePermission = Manifest.permission.RECORD_AUDIO;
            int hasCamPermission = checkSelfPermission(cameraPermission);
            int hasMicPermission = checkSelfPermission(microphonePermission);
            List<String> permissions = new ArrayList<String>();
            if (hasCamPermission != PackageManager.PERMISSION_GRANTED) {
                permissions.add(cameraPermission);
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.CAMERA)) {
                    // Provide rationale in Snackbar to request permission
//                    Snackbar.make(preview, R.string.permission_camera_rationale,
//                            Snackbar.LENGTH_INDEFINITE).show();
                } else {
                    // Explain in Snackbar to turn on permission in settings
//                    Snackbar.make(preview, R.string.permission_camera_explain,
//                            Snackbar.LENGTH_INDEFINITE).show();
                }
            }
            if (hasMicPermission != PackageManager.PERMISSION_GRANTED) {
                permissions.add(microphonePermission);
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
                    // Provide rationale in Snackbar to request permission
//                    Snackbar.make(preview, R.string.permission_microphone_rationale,
//                            Snackbar.LENGTH_INDEFINITE).show();
                } else {
                    // Explain in Snackbar to turn on permission in settings
//                    Snackbar.make(preview, R.string.permission_microphone_explain,
//                            Snackbar.LENGTH_INDEFINITE).show();
                }
            }
            if (!permissions.isEmpty()) {
                String[] params = permissions.toArray(new String[permissions.size()]);
                ActivityCompat.requestPermissions(this, params, REQUEST_CAMERA_MICROPHONE);
            } else {
                // We already have permission, so handle as normal
                streamerService.startStreaming(rtmpUrl);
            }
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_MICROPHONE: {
                Log.i(MainActivity.APP_NAME, "Received response for camera with mic permissions request.");

                // We have requested multiple permissions for contacts, so all of them need to be
                // checked.
                if (Utils.verifyPermissions(grantResults)) {
                    // permissions were granted, yay! do the
                    // streamer task you need to do.
                    streamerService.startStreaming(rtmpUrl);
                } else {
                    Log.i(MainActivity.APP_NAME, "Camera with mic permissions were NOT granted.");
//                    Snackbar.make(preview, R.string.permissions_not_granted,
//                            Snackbar.LENGTH_SHORT)
//                            .show();
                }
                break;
            }

            // other 'switch' lines to check for other
            // permissions this app might request
        }
        // return;
    }


    private void stopStreaming() {
        Log.d(MainActivity.APP_NAME, "stopStreaming");

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        if (streamerService.isStreaming()) {
            streamerService.stopStreaming();
        }
    }

    public void endEvent(View view) {
        Intent data = new Intent();
        data.putExtra(YouTubeApi.BROADCAST_ID_KEY, broadcastId);
        if (getParent() == null) {
            setResult(Activity.RESULT_OK, data);
        } else {
            getParent().setResult(Activity.RESULT_OK, data);
        }
        finish();
    }
    private void startBackgroundThread(){
        backgroundHandlerThread = new HandlerThread("YT-WatchMe");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }
    private void stopBackgroundThread(){
        backgroundHandlerThread.quitSafely();
        try{
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    private static int sensorToDeeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation ){
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360)%360;

    }
    private static Size chooseOptimalSize(Size[] choices, int width, int height){
        List<Size> bigEnough = new ArrayList<Size>();
        for(Size option : choices){
            if(option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height){
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() >0){
            return Collections.min(bigEnough, new CompareSizeByArea());
        }else{
            return choices[0];
        }
    }

    private static class CompareSizeByArea implements Comparator<Size>{

        @Override
        public int compare(Size lhs, Size rhs){
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}