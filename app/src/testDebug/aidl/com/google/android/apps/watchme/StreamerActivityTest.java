package com.google.android.apps.watchme;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;
import android.hardware.camera2.*;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import junit.framework.TestCase;

/**
 * Created by edsonandrade on 5/3/17.
 */
public class StreamerActivityTest extends TestCase {

    private static final String TAG = "StreamerActivityTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private CameraManager mCameraManager;
    private CameraDevice cameraDevice;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private CameraDevice.StateCallback mStateCallback;



    public void testCloseCamera() throws Exception {
        cameraDevice.close();
        assertEquals(null, cameraDevice);
    }

    public void testOnResume() throws Exception {

    }

    public void testOnPause() throws Exception {

    }

    public void testOnCreate() throws Exception {
        //StreamerActivity.startPreview();
    }

    public void testOnDestroy() throws Exception {

    }

    public void testOnRequestPermissionsResult() throws Exception {

    }

    public void testEndEvent() throws Exception {

    }

}
