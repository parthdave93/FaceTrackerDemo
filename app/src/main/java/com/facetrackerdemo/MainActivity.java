package com.facetrackerdemo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.os.Environment.getExternalStorageDirectory;
import static java.io.File.separator;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FaceTrackerDemo";
    private CameraSource mCameraSource = null;
    private CameraSurfacePreview mPreview;
    private CameraOverlay cameraOverlay;
    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    public static final int APP_PERMISSION = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreview = (CameraSurfacePreview) findViewById(R.id.preview);
        cameraOverlay = (CameraOverlay) findViewById(R.id.faceOverlay);
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }


    private void requestCameraPermission() {

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(cameraOverlay, "Camera permission is required",
                Snackbar.LENGTH_INDEFINITE)
                .setAction("OK", listener)
                .show();
    }


    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new MainActivity.GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {

            Log.e(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        for (int i = 0, len = permissions.length; i < len; i++) {
    
            String permission = permissions[i];
            if (Manifest.permission.CAMERA.equals(permission) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted - initialize the camera source");
                createCameraSource();
                return;
            }else{
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                };
    
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("FaceTrackerDemo")
                       .setMessage("Need Camera access permission!")
                       .setPositiveButton("OK", listener)
                       .show();
            }
            if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                onCaptureClick(null);
            }else{
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("FaceTrackerDemo")
                       .setMessage("Need Storage access permission!")
                       .setPositiveButton("OK",null)
                       .show();
            }
        }
    
        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

    }


    private void startCameraSource() {
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, cameraOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new MainActivity.GraphicFaceTracker(cameraOverlay);
        }
    }

    private class GraphicFaceTracker extends Tracker<Face> {
        private CameraOverlay mOverlay;
        private FaceOverlayGraphics faceOverlayGraphics;

        GraphicFaceTracker(CameraOverlay overlay) {
            mOverlay = overlay;
            faceOverlayGraphics = new FaceOverlayGraphics(overlay);
        }

        @Override
        public void onNewItem(int faceId, Face item) {
            faceOverlayGraphics.setId(faceId);
        }

        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(faceOverlayGraphics);
            faceOverlayGraphics.updateFace(face);
        }

        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(faceOverlayGraphics);
        }

        @Override
        public void onDone() {
            mOverlay.remove(faceOverlayGraphics);
        }
    }
    
    public void onCaptureClick(View view){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            takeSnap();
        }else{
            ActivityCompat.requestPermissions(this,
                                              new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                              APP_PERMISSION);
        }
       
    }
    
    private void takeSnap() {
        mCameraSource.takePicture(new CameraSource.ShutterCallback() {
            @Override public void onShutter() {
                //make a shutter sound if you want to
            }
        }, new CameraSource.PictureCallback() {
            @Override public void onPictureTaken(byte[] bytes) {
                try {
                    String directoryPath = getExternalStorageDirectory() + separator + "Dummy_path"+ separator;
                    File directory = new File(directoryPath);
                    //create directory if not exists
                    if (!directory.exists())
                        directory.mkdirs();
                    File captureFile = new File(directoryPath + "img_" + System.currentTimeMillis() + ".jpg");
                    
                    //create file if not exists
                    if (!captureFile.exists())
                        captureFile.createNewFile();
                    FileOutputStream stream = new FileOutputStream(captureFile);
                    stream.write(bytes);
                    stream.flush();
                    stream.close();
    
                    //opening that file replace below code with what you want to do
                    Intent intent = new Intent();
                    intent.setAction(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(captureFile), "image/*");
                    startActivity(intent);
                    finish();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
