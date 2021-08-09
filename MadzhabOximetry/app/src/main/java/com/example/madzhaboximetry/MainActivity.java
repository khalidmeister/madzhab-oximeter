package com.example.madzhaboximetry;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";
    private static int CAMERA_PERMISSION_CODE = 100;
    private static int VIDEO_RECORD_CODE = 101;
    private Uri videoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Loader.load(opencv_java.class);

        if (isCameraPresentInPhone()) {
            Log.i("VIDEO_RECORD_TAG", "Camera exists");
            getCameraPermission();
        } else {
            Log.i("VIDEO_RECORD_TAG", "Camera doesn't exist");
        }
    }

    public void recordVideoButtonPressed(View view) {
        recordVideo();
    }

    private boolean isCameraPresentInPhone() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private void getCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_CODE);
        }
    }

    // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
    ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @RequiresApi(api = Build.VERSION_CODES.R)
                @Override
                public void onActivityResult(ActivityResult result) {
                    Intent data = result.getData();
                    assert data != null;
                    videoUri = data.getData();

                    String path = getFilePathFromContentUri(videoUri, getContentResolver());

                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.i("VIDEO_RECORD_TAG", "Video exists at " + path);
                    } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        Log.i("VIDEO_RECORD_TAG", "Recording video is cancelled");
                    } else {
                        Log.i("VIDEO_RECORD_TAG", "Recording video got some error");
                    }

                    FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path);
                    OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();

                    try {
                        grabber.start();
                    } catch (FrameGrabber.Exception e) {
                        Log.i("VIDEO_RECORD_TAG", e.toString());
                        e.printStackTrace();
                    }

                    double dcR = 0;
                    double dcB = 0;
                    double acR = 0;
                    double acB = 0;

                    int count = 0;
                    for(int i=0; i < grabber.getLengthInFrames(); i++) {
                        Frame nthFrame = null;
                        try {
                            nthFrame = grabber.grabImage();
                        } catch (FFmpegFrameGrabber.Exception e) {
                            e.printStackTrace();
                        }

                        if(nthFrame == null) {
                            continue;
                        } else {
                            Mat mat = converterToMat.convertToOrgOpenCvCoreMat(nthFrame);

                            // Crop the Image in center with size 50x50
                            int x = mat.rows() / 2;
                            int y = mat.cols() / 2;
                            int w = 100 / 2;
                            int h = 100 / 2;
                            mat = mat.submat(x - w, x + w, y - h, y + h);

                            // Change the color space from BGR to RGB
                            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);

                            // Get the Red and Blue band
                            List<Mat> rgb = new ArrayList<>(3);
                            Core.split(mat, rgb);

                            // Calculate the mean and std
                            MatOfDouble meanRgb = new MatOfDouble();
                            MatOfDouble stdRgb = new MatOfDouble();
                            Core.meanStdDev(mat, meanRgb, stdRgb);

                            // Catch the intermediate result
                            dcR += meanRgb.get(0, 0)[0];
                            dcB += meanRgb.get(2, 0)[0];
                            acR += stdRgb.get(0, 0)[0];
                            acB += stdRgb.get(2, 0)[0];
                            count++;
                        }
                    }

                    Log.i("VIDEO_RECORD_TAG", "Jumlah Frame: " + count);

                    // Calculate the average
                    dcR = dcR / count;
                    dcB = dcB / count;
                    acR = acR / count;
                    acB = acB / count;

                    Log.i("VIDEO_RECORD_TAG", "DC Red: " + dcR);
                    Log.i("VIDEO_RECORD_TAG", "DC Blue: " + dcB);

                    // Hitung SPO2
                    double spo2 = 96.87193145 + (3.08854472 * ( (acR / dcR) / (acB / dcB) ));
                    ((TextView)findViewById(R.id.hasilSpo2)).setText("Nilai SPO2 anda sebesar " + String.format("%.2f", spo2));
                    Log.i("VIDEO_RECORD_TAG", "Nilai SPO2 anda sebesar " + spo2);
                }
            });

    public void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 15);
        someActivityResultLauncher.launch(intent);
    }

    public static String getFilePathFromContentUri(Uri contentUri, ContentResolver contentResolver) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};

        Cursor cursor = contentResolver.query(contentUri, filePathColumn, null, null, null);

        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }
}