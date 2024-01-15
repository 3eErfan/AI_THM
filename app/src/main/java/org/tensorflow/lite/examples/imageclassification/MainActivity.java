/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.imageclassification;

import static java.lang.Runtime.getRuntime;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.examples.imageclassification.databinding.ActivityMainBinding;

import java.io.IOException;
import java.util.Objects;

/*
TODO: General Functionality
    - Start/Stop classification
    - Add model for classification

TODO: Experiment specification
    - runTestConfiguration
    - setTaskToDelegate
    - setTaskPeriod
 */

/** Entrypoint for app */
public class MainActivity extends AppCompatActivity {
    PFManager pfManager;
    DataProcessor dataProcessor;
    String currentThermalStatus = "None";
    String currentFolder;
    String documentsFolder;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());

        try {
            Process process = getRuntime().exec("su -c setenforce 0");
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        currentFolder = Objects.requireNonNull(getExternalFilesDir(null)).getAbsolutePath();
        documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
        dataProcessor = new DataProcessor(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                this.pfManager = new PFManager(this);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    protected void onResume() {
        System.out.println("in resume");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean result = pfManager.registerListener(this);
            if (!result) {
                System.out.println("Failed to register thermal status listener.");
            }
        }

        super.onResume();
    }

    protected void onPause() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean result = pfManager.unregisterListener(this);
            if (!result) {
                System.out.println("Failed to unregister thermal status listener.");
            }
        }

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

}
