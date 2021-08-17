package com.example.madzhaboximetry;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.number.Scale;
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
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.imgproc.Imgproc.INTER_AREA;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";
    private static int CAMERA_PERMISSION_CODE = 100;
    private static int VIDEO_RECORD_CODE = 101;
    private Uri videoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
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

                    for(int i=0; i < grabber.getLengthInFrames(); i++) {
                        Frame nthFrame = null;
                        try {
                            nthFrame = grabber.grabImage();
                        } catch (FFmpegFrameGrabber.Exception e) {
                            e.printStackTrace();
                        }

                        if(nthFrame == null) {
                        } else {
                            Mat mat = converterToMat.convertToOrgOpenCvCoreMat(nthFrame);

                            Log.i("VIDEO_RECORD_TAG", mat.rows() + "x" + mat.cols());

                            int x = mat.rows() / 4;
                            int y = mat.cols() / 3;
                            double p_max = 0;
                            int m_max = 0;
                            int n_max = 0;

                            // Get the best ROI
                            if (i==0) {
                                Mat temp = mat;

                                // Get the Red and Blue band
                                List<Mat> rgb = new ArrayList<>(3);
                                Core.split(mat, rgb);
                                temp = rgb.get(2);

                                // Calculate the mean and std
                                MatOfDouble meanRgb = new MatOfDouble();
                                MatOfDouble stdRgb = new MatOfDouble();
                                Core.meanStdDev(mat, meanRgb, stdRgb);

                                Mat thresh = new Mat();

                                Imgproc.threshold(temp, thresh, meanRgb.get(0, 0)[0], 255, Imgproc.THRESH_BINARY);

                                int xt = thresh.rows() / 4;
                                int yt = thresh.cols() / 3;

                                // Get the best ROI
                                for(int m = 0; m < 4; m++) {
                                    for(int n = 0; n < 3; n++) {
                                        Mat new_temp = thresh.submat(xt*(m), xt*(m+1), yt*(n), yt*(n+1));

                                        MatOfDouble mu = new MatOfDouble();
                                        MatOfDouble sigma = new MatOfDouble();
                                        Core.meanStdDev(new_temp, mu, sigma);

                                        double p = Math.abs(255/2 - mu.get(0, 0)[0]);

                                        if (m == 0 && n == 0) {
                                            p_max = p;
                                            m_max = m;
                                            n_max = n;
                                        } else {
                                            if (p < p_max) {
                                                p_max = p;
                                                m_max = m;
                                                n_max = n;
                                            }
                                        }
                                    }
                                }
                                Log.i("VIDEO_RECORD_TAG", "The Best ROI: " + m_max + " " + n_max);
                            }

                            // Crop the Image with the ROI
                            mat = mat.submat(x * (m_max), x * (m_max+1), y * (n_max), y * (n_max+1));

                            // Change the color space from BGR to RGB
                            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);

                            // Validate the size
                            Log.i("VIDEO_RECORD_TAG", mat.rows() + "x" + mat.cols());

                            // Get the Red and Blue band
                            List<Mat> rgb = new ArrayList<>(3);
                            Core.split(mat, rgb);

                            // Calculate the mean and std
                            MatOfDouble meanRgb = new MatOfDouble();
                            MatOfDouble stdRgb = new MatOfDouble();
                            Core.meanStdDev(mat, meanRgb, stdRgb);

                            List<Double> sigRed = new ArrayList<Double>();
                            List<Double> sigBlue = new ArrayList<Double>();

                            sigRed.add(meanRgb.get(0, 0)[0]);
                            sigBlue.add(meanRgb.get(2, 0)[0]);

                            Log.i("SIGNAL_RED", String.valueOf(meanRgb.get(0, 0)[0]));
                            Log.i("SIGNAL_BLUE", String.valueOf(meanRgb.get(2, 0)[0]));
                        }
                    }
//                    Log.i("VIDEO_RECORD_TAG", "Jumlah Frame: " + count);
//
//                    // Calculate the average
//                    dcR = dcR / count;
//                    dcB = dcB / count;
//                    acR = acR / count;
//                    acB = acB / count;
//
//                    Log.i("VIDEO_RECORD_TAG", "DC Red: " + dcR);
//                    Log.i("VIDEO_RECORD_TAG", "DC Blue: " + dcB);
//
//                    // Hitung SPO2
//                    double spo2 = 96.87193145 + (3.08854472 * ( (acR / dcR) / (acB / dcB) ));
//                    ((TextView)findViewById(R.id.hasilSpo2)).setText("Nilai SPO2 anda sebesar " + String.format("%.2f", spo2));
//                    Log.i("VIDEO_RECORD_TAG", "Nilai SPO2 anda sebesar " + spo2);
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