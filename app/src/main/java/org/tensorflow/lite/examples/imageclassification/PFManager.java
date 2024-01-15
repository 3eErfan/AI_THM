/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.imageclassification;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.PowerManager;

import androidx.annotation.RequiresApi;

import java.io.IOException;

// A manager class that managers PF APIs in Java code.
// The class managers thermal throttle status listener and other PF related tasks.
@RequiresApi(api = VERSION_CODES.Q)
public class PFManager implements PowerManager.OnThermalStatusChangedListener {
    MainActivity mainActivity;
    DataProcessor dataProcessor;

    public PFManager(MainActivity activity) throws IOException, InterruptedException {
        mainActivity = activity;
        dataProcessor = activity.dataProcessor;
    }

    // Thermal status change listener.
    public void onThermalStatusChanged(int i) {
        try {
            System.out.println("Thermal Status: " + i);
            mainActivity.currentThermalStatus = getThermalStatusName(i);
            dataProcessor.processDataCollection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean registerListener(Context context) {
        // Retrieve power manager and register thermal state change callback.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.addThermalStatusListener(this);
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean unregisterListener(Context context) {
        // Remove the thermal state change listener on pause.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.removeThermalStatusListener(this);
            }
            return true;
        } else {
            return false;
        }
    }

    private String getThermalStatusName(int status) {
        String currentStatus = "Unknown";
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE:
                currentStatus = "None";
                break;
            case PowerManager.THERMAL_STATUS_LIGHT:
                currentStatus = "Light";
                break;
            case PowerManager.THERMAL_STATUS_MODERATE:
                currentStatus = "Moderate";
                break;
            case PowerManager.THERMAL_STATUS_SEVERE:
                currentStatus = "Severe";
                break;
            case PowerManager.THERMAL_STATUS_CRITICAL:
                currentStatus = "Critical";
                break;
            case PowerManager.THERMAL_STATUS_EMERGENCY:
                currentStatus = "Emergency";
                break;
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                currentStatus = "Shutdown";
                break;
        }
        return currentStatus;
    }

}
