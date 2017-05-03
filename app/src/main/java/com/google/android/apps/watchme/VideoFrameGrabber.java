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

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
//import android.hardware.Camera;
//import android.hardware.Camera.Size;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         VideoFrameGrabber class which grabs video frames to buffer.
 */
public class VideoFrameGrabber {
    // Member variables
//    private Camera camera;
    private CameraManager cameraManager;
    private FrameCallback frameCallback;
    String cameraIDs;

    public void setFrameCallback(FrameCallback callback) {
        frameCallback = callback;
    }

    /**
     * Starts camera recording to buffer.
     *
     * @param camera - Camera to be recorded.
     * @return preview size.
     */
    public Size start(CameraManager camera) {
        cameraManager = camera;

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraIDs : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIDs);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)== CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth, rotatedHeight);
                cameraId = cameraIDs;


        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIDs);
        Camera.Parameters params = camera.getParameters();
        params.setPreviewSize(StreamerActivity.CAMERA_WIDTH, StreamerActivity.CAMERA_HEIGHT);
        camera.setParameters(params);

        Size previewSize = params.getPreviewSize();
        int bufferSize = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(
                params.getPreviewFormat());
        camera.addCallbackBuffer(new byte[bufferSize]);

        camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] yuv_image, Camera camera) {
                if (frameCallback != null) {
                    frameCallback.handleFrame(yuv_image);
                }
                camera.addCallbackBuffer(yuv_image);
            }
        });

        return previewSize;
    }

    public void stop() {
        camera.setPreviewCallbackWithBuffer(null);
        camera = null;
    }

    public interface FrameCallback {
        void handleFrame(byte[] yuv_image);
    }
}
