# Madzhab Oximeter: An Android-Based Oximeter Application
## Introduction
Madzhab Oximeter is an application for calculating oxygen saturation (SPO2) by using the smartphone's camera. The app records a video of your finger for 5 seconds, and then it calculates the SPO2 based on the red and blue channel from the video. This repository contains the prototype, which is on a jupyter notebook format, and also the application itself that is based on Android (Java).

## Technical Side
FYI, the raw size of the project folder is 1.69 GB. Because of that, I will not share the project folder. But, I will show you the code snippets of our work.

- First, the .java file. It contains the mechanisms of the app.
```java
package com.example.cobalagi;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";
    private static int CAMERA_PERMISSION_CODE = 100;
    private static int VIDEO_RECORD_CODE = 101;
    private Uri videoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        if (OpenCVLoader.initDebug()) {
//            Log.d(TAG, "OpenCV Installed Successfully");
//        } else {
//            Log.d(TAG, "OpenCV Not Installed");
//        }

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
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            return true;
        } else {
            return false;
        }
    }

    private void getCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}, CAMERA_PERMISSION_CODE);
        }
    }

    // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
    ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    Intent data = result.getData();
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
                    AndroidFrameConverter converterToBitmap = new AndroidFrameConverter();
                    OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();

                    try {
                        grabber.start();
                    } catch (FrameGrabber.Exception e) {
                        Log.i("VIDEO_RECORD_TAG", e.toString());
                        e.printStackTrace();
                    }

                    Log.i("VIDEO_RECORD_TAG", Integer.toString(grabber.getLengthInFrames()));

//                    VideoCapture new_video = new VideoCapture(path);
//                    if (new_video.isOpened()) {
//                        Log.i("VIDEO_RECORD_TAG", "Video exists");
//                    } else {
//                        Log.i("VIDEO_RECORD_TAG", "Video doesn't exist");
//                    }
                }
            });

    public void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 5);
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
```

- Second, the AndroidManifest.xml file. It contains the information of our app, and also the permissions that we need from the users.
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.cobalagi">

    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:name="android.support.multidex.MultiDexApplication"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Cobalagi">
        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```


- Third, is the build.gradle file. This file contains the libraries that we need for our app.
```
plugins {
    id 'com.android.application'
}

android {
    compileSdkVersion 30
    buildToolsVersion "30.0.3"

    defaultConfig {
        applicationId "com.example.cobalagi"
        minSdkVersion 16
        targetSdkVersion 30
        versionCode 1
        versionName "1.0"
        multiDexEnabled true

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
    packagingOptions {
        exclude 'META-INF/DEPENDENCIES'
        exclude 'META-INF/LICENSE'
        exclude 'META-INF/LICENSE.txt'
        exclude 'META-INF/license.txt'
        exclude 'META-INF/NOTICE'
        exclude 'META-INF/NOTICE.txt'
        exclude 'META-INF/notice.txt'
        exclude 'META-INF/ASL2.0'
        pickFirst 'androidsupportmultidexversion.txt'
        pickFirst 'META-INF/native-image/android-x86/jnijavacpp/jni-config.json'
        pickFirst 'META-INF/native-image/android-x86/jnijavacpp/reflect-config.json'

        pickFirst 'META-INF/native-image/android-x86_64/jnijavacpp/jni-config.json'
        pickFirst 'META-INF/native-image/android-x86_64/jnijavacpp/reflect-config.json'

        pickFirst 'META-INF/native-image/android-arm64/jnijavacpp/jni-config.json'
        pickFirst 'META-INF/native-image/android-arm64/jnijavacpp/reflect-config.json'

        pickFirst 'META-INF/native-image/android-arm/jnijavacpp/jni-config.json'
        pickFirst 'META-INF/native-image/android-arm/jnijavacpp/reflect-config.json'

        exclude 'META-INF/services/javax.annotation.processing.Processor'
        pickFirst  'META-INF/maven/org.bytedeco.javacpp-presets/opencv/pom.properties'
        pickFirst  'META-INF/maven/org.bytedeco.javacpp-presets/opencv/pom.xml'
        pickFirst  'META-INF/maven/org.bytedeco.javacpp-presets/ffmpeg/pom.properties'
        pickFirst  'META-INF/maven/org.bytedeco.javacpp-presets/ffmpeg/pom.xml'
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    splits {
        abi {
            enable true
            reset()
            include 'x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a'
            universalApk false
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    sourceSets {
        main {
            jni {
                srcDirs 'src\\main\\jni', 'src\\main\\jniLibs'
            }
        }
    }
}

dependencies {

    implementation 'androidx.appcompat:appcompat:1.3.1'
    implementation 'com.google.android.material:material:1.4.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.0.4'
    implementation 'com.android.support:multidex:1.0.3'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.3'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.4.0'

    implementation group: 'org.bytedeco', name: 'javacv', version: '1.5.5'
    implementation group: 'org.bytedeco', name: 'opencv', version: '4.5.1-1.5.5'
    implementation group: 'org.bytedeco', name: 'opencv', version: '4.5.1-1.5.5', classifier: 'android-arm'
    implementation group: 'org.bytedeco', name: 'opencv', version: '4.5.1-1.5.5', classifier: 'android-arm64'
    implementation group: 'org.bytedeco', name: 'opencv', version: '4.5.1-1.5.5', classifier: 'android-x86'
    implementation group: 'org.bytedeco', name: 'opencv', version: '4.5.1-1.5.5', classifier: 'android-x86_64'
    implementation group: 'org.bytedeco', name: 'openblas', version: '0.3.13-1.5.5'
    implementation group: 'org.bytedeco', name: 'openblas', version: '0.3.13-1.5.5', classifier: 'android-arm'
    implementation group: 'org.bytedeco', name: 'openblas', version: '0.3.13-1.5.5', classifier: 'android-arm64'
    implementation group: 'org.bytedeco', name: 'openblas', version: '0.3.13-1.5.5', classifier: 'android-x86'
    implementation group: 'org.bytedeco', name: 'openblas', version: '0.3.13-1.5.5', classifier: 'android-x86_64'
    implementation group: 'org.bytedeco', name: 'ffmpeg', version: '4.3.2-1.5.5'
    implementation group: 'org.bytedeco', name: 'ffmpeg', version: '4.3.2-1.5.5', classifier: 'android-arm'
    implementation group: 'org.bytedeco', name: 'ffmpeg', version: '4.3.2-1.5.5', classifier: 'android-arm64'
    implementation group: 'org.bytedeco', name: 'ffmpeg', version: '4.3.2-1.5.5', classifier: 'android-x86'
    implementation group: 'org.bytedeco', name: 'ffmpeg', version: '4.3.2-1.5.5', classifier: 'android-x86_64'
}
```

- Lastly, the .xml file. This file describes the interface for our app.
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/textView"
        android:layout_width="303dp"
        android:layout_height="81dp"
        android:gravity="center"
        android:text="Silakan ukur saturasi oksigen anda dengan menekan tombol REKAM di bawah"
        android:textAppearance="@style/TextAppearance.AppCompat.Medium"
        app:layout_constraintBottom_toTopOf="@+id/button"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_bias="0.743" />

    <Button
        android:id="@+id/button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="220dp"
        android:onClick="recordVideoButtonPressed"
        android:text="Rekam"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintHorizontal_bias="0.498"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```
