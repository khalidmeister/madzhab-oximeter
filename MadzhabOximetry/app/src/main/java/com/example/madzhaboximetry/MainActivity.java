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
import androidx.constraintlayout.motion.utils.LinearCurveFit;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

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
import java.util.Arrays;
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

                    List<Entry> dataRed = new ArrayList<>();
                    List<Double> sigRed = new ArrayList<Double>();
                    List<Double> sigBlue = new ArrayList<Double>();

                    // Retrieving Signals
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

                            // Get the Red and Blue band
                            List<Mat> rgb = new ArrayList<>(3);
                            Core.split(mat, rgb);

                            // Calculate the mean and std
                            MatOfDouble meanRgb = new MatOfDouble();
                            MatOfDouble stdRgb = new MatOfDouble();
                            Core.meanStdDev(mat, meanRgb, stdRgb);

                            sigRed.add(meanRgb.get(0, 0)[0]);
                            sigBlue.add(meanRgb.get(2, 0)[0]);
                        }
                    }

                    // Get the FPS
                    int N = grabber.getLengthInFrames();
                    int fps = N / 15;
                    Log.i("VIDEO_RECORD_TAG", "# of N: " + N);

//                    // Polynomial Curve Fitting
//                    double[] pcfRed = pcf(N, sigRed);
//                    double[] pcfBlue = pcf(N, sigBlue);
//
//                    List<Double> sigRedClean = new ArrayList<>();
//                    List<Double> sigBlueClean = new ArrayList<>();
//
//                    double temp;
//
//                    for(int j = 0; j < N; j++) {
//                        temp = sigRed.get(j) - pcfRed[j];
//                        sigRedClean.add(temp);
//
//                        temp = sigBlue.get(j) - pcfBlue[j];
//                        sigBlueClean.add(temp);
//                    }

                    // Get the minimum number of signals
                    int nFFT = 1;
                    while(nFFT < N) {
                        nFFT *= 2;
                    }
                    nFFT /= 2;

                    Log.i("VIDEO_RECORD_TAG", "# of fps: " + fps);
                    Log.i("VIDEO_RECORD_TAG", "# for FFT: " + nFFT);

                    // Nanti ambil data dari detik ke-3
                    sigRed = sigRed.subList((fps * 3) - 1, N-1);
                    sigBlue = sigBlue.subList((fps * 3) - 1, N-1);

                    // Sama, sekalian dengan pengambilan nilai yang termasuk batas kelipatan ke dua minimum;
                    sigRed = sigRed.subList(0, nFFT);
                    sigBlue = sigBlue.subList(0, nFFT);

                    // Calculate FFT
                    FFT fft = new FFT(nFFT);

                    double[] window = fft.getWindow();
                    double[] imRed = new double[nFFT];
                    double[] imBlue = new double[nFFT];

                    fft.fft(sigRed, imRed);
                    fft.fft(sigBlue, imBlue);

                    // Set to absolute to all values
                    double[] sigRedAbs = new double[nFFT];
                    double[] sigBlueAbs = new double[nFFT];

                    for(int j=0; j < nFFT; j++) {
                        sigRedAbs[j] = Math.abs(sigRed.get(j));
                        sigBlueAbs[j] = Math.abs(sigBlue.get(j));
                    }

                    // Display The Result
                    Log.i("VIDEO_RECORD_TAG", "Signal Red (PCF + FFT): " + Arrays.toString(sigRedAbs));
                    Log.i("VIDEO_RECORD_TAG", "Signal Blue (PCF + FFT): " + Arrays.toString(sigBlueAbs));

                    // Get the DC component
                    double dcRed = sigRedAbs[0];
                    double dcBlue = sigBlueAbs[0];

                    // Get the AC component
                    double acRed = 0;
                    double acBlue = 0;

                    int indexRed = 0;
                    int indexBlue = 0;

                    for (int j = 1; j < nFFT - 1; j++) {
                        double freq = j * fps / nFFT;
                        if(freq >= 1.0 && freq <= 2.0) {
                            if(sigRedAbs[j] > sigRedAbs[j-1] && sigRedAbs[j] > sigRedAbs[j+1]) {
                                if(sigRedAbs[j] > acRed) {
                                    acRed = sigRedAbs[j];
                                    indexRed = j;
                                }
                            }
                            if(sigBlueAbs[j] > sigBlueAbs[j-1] && sigBlueAbs[j] > sigBlueAbs[j+1]) {
                                if(sigBlueAbs[j] > acBlue) {
                                    acBlue = sigBlueAbs[j];
                                    indexBlue = j;
                                }
                            }
                        }
                    }

                    Log.i("VIDEO_RECORD_TAG", "DC Red: " + dcRed);
                    Log.i("VIDEO_RECORD_TAG", "DC Blue: " + dcBlue);
                    Log.i("VIDEO_RECORD_TAG", "AC Red: " + acRed + " Index: " + indexRed);
                    Log.i("VIDEO_RECORD_TAG", "AC Blue: " + acBlue + " Index: " + indexBlue);

                    // Hitung SPO2
                    double spo2 = 110 - 25 * ((acRed / dcRed) / (acBlue / dcBlue));
                    ((TextView)findViewById(R.id.hasilSpo2)).setText("Nilai SPO2 anda sebesar " + String.format("%.2f", spo2));
                    Log.i("VIDEO_RECORD_TAG", "Nilai SPO2 anda sebesar " + spo2);
                }
            });

    public void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 15);
        someActivityResultLauncher.launch(intent);
    }

    private static double min(double[] array) {
        double min = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] < min) {
                min = array[i];
            }
        }
        return min;
    }

    private static double max(double[] array) {
        double max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
            }
        }
        return max;
    }

//    private static double[] pcf(int N, List<Double> signal) {
//        double[] x = new double[N];
//        double[] y = new double[N];
//
//        for (int i = 0; i < N; i++) {
//            x[i] = i;
//            y[i] = signal.get(i);
//        }
//
//        int n = 3;
//        double X[] = new double[2 * n + 1];
//        for (int i = 0; i < 2 * n + 1; i++) {
//            X[i] = 0;
//            for (int j = 0; j < N; j++)
//                X[i] = X[i] + Math.pow(x[j], i);        //consecutive positions of the array will store N,sigma(xi),sigma(xi^2),sigma(xi^3)....sigma(xi^2n)
//        }
//
//        double[][] B = new double[n + 1][n + 2];            //B is the Normal matrix(augmented) that will store the equations, 'a' is for value of the final coefficients
//        double[] a = new double[n + 1];
//        for (int i = 0; i <= n; i++)
//            for (int j = 0; j <= n; j++)
//                B[i][j] = X[i + j];            //Build the Normal matrix by storing the corresponding coefficients at the right positions except the last column of the matrix
//
//        double[] Y = new double[n + 1];                    //Array to store the values of sigma(yi),sigma(xi*yi),sigma(xi^2*yi)...sigma(xi^n*yi)
//        for (int i = 0; i < n + 1; i++) {
//            Y[i] = 0;
//            for (int j = 0; j < N; j++)
//                Y[i] = Y[i] + Math.pow(x[j], i) * y[j];        //consecutive positions will store sigma(yi),sigma(xi*yi),sigma(xi^2*yi)...sigma(xi^n*yi)
//        }
//
//        for (int i = 0; i <= n; i++)
//            B[i][n + 1] = Y[i];                //load the values of Y as the last column of B(Normal Matrix but augmented)
//        n = n + 1;
//
//        for (int i = 0; i < n; i++)                    //From now Gaussian Elimination starts(can be ignored) to solve the set of linear equations (Pivotisation)
//            for (int k = i + 1; k < n; k++)
//                if (B[i][i] < B[k][i])
//                    for (int j = 0; j <= n; j++) {
//                        double temp = B[i][j];
//                        B[i][j] = B[k][j];
//                        B[k][j] = temp;
//                    }
//
//        for (int i = 0; i < n - 1; i++)            //loop to perform the gauss elimination
//            for (int k = i + 1; k < n; k++) {
//                double t = B[k][i] / B[i][i];
//                for (int j = 0; j <= n; j++)
//                    B[k][j] = B[k][j] - t * B[i][j];    //make the elements below the pivot elements equal to zero or elimnate the variables
//            }
//
//        for (int i = n - 1; i >= 0; i--)                //back-substitution
//        {                        //x is an array whose values correspond to the values of x,y,z..
//            a[i] = B[i][n];                //make the variable to be calculated equal to the rhs of the last equation
//            for (int j = 0; j < n; j++)
//                if (j != i)            //then subtract all the lhs values except the coefficient of the variable whose value                                   is being calculated
//                    a[i] = a[i] - B[i][j] * a[j];
//            a[i] = a[i] / B[i][i];            //now finally divide the rhs by the coefficient of the variable to be calculated
//        }
//
//        int min_x = (int) min(x);
//        int max_x = (int) max(x);
//
//        double[] pcfResult = new double[N];
//
//        for(int i = min_x; i < max_x; i++) {
//            double yp=a[0];
//            for (int j=1; j<n; j++) {
//                yp = yp + Math.pow(i, j) * a[j];
//            }
//            pcfResult[i] = yp;
//        }
//
//        return pcfResult;
//    }

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