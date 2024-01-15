package org.tensorflow.lite.examples.imageclassification;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DataProcessor {

    MainActivity mainActivity;
    String performanceFilePath;
    String rawFilePath;
    SimpleDateFormat dateFormat;
    String fileSeries;
    String performanceFileName = "Performance_Measurements";
    String rawDataFileName = "Raw_Data";
    String[] thermalZonePaths;
    String[] thermalZoneTypesOfInterest = {"BIG", "MID", "LITTLE", "TPU", "G3D"};
    StringBuilder thermalZoneTypeHeaders;
    String[] cpuDevicePaths;
    List<Float> initialFreqTimes;
    Boolean isRooted;
    String rootAccess;
    long startTimeSecs;

    public DataProcessor(MainActivity activity) {
        mainActivity = activity;

        isRooted = true;
        rootAccess = "";

        dateFormat = new SimpleDateFormat("HH:mm:ss");
        fileSeries = dateFormat.format(new Date());
        String[] timeStampSplit = fileSeries.split(":");
        startTimeSecs = Long.parseLong(timeStampSplit[0]) * 3600 +
                Long.parseLong(timeStampSplit[1]) * 60 +
                Long.parseLong(timeStampSplit[2]);

//        String currentFolder = mainActivity.currentFolder;
        String currentFolder = mainActivity.documentsFolder;
        performanceFilePath = currentFolder + File.separator +
                performanceFileName + startTimeSecs + ".csv";
        rawFilePath = currentFolder + File.separator +
                rawDataFileName + startTimeSecs + ".csv";

        try {
            thermalZonePaths = getThermalZoneFilePaths("/sys/class/thermal");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            cpuDevicePaths = getCPUDeviceFiles("/sys/devices/system/cpu");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Create string for thermal zone type headers
        thermalZoneTypeHeaders = new StringBuilder();
        for (String thermalZoneType: thermalZoneTypesOfInterest) {
            thermalZoneTypeHeaders.append(thermalZoneType).append("Temperature,");
        }

        // Create file for data collection
        String FILEPATH = performanceFilePath;
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, false))) {
            String sb = "time" +
                    ',' +
                    "relativeTime" +
                    ',' +
                    "thermalStatus" +
                    ',' +
                    thermalZoneTypeHeaders +
                    "cpuFrequency" +
                    ',' +
                    "gpuFrequency" +
                    ',' +
                    "cpuUtilization" +
                    ',' +
                    "gpuUtilization" +
                    '\n';
            writer.write(sb);
            System.out.println("Creating " + performanceFileName + " done!");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }


        // Create headers for raw data
        StringBuilder thermalZoneTypes = new StringBuilder();
        for (String currThermalZone: thermalZonePaths) {
            try {
                String currThermalZoneType = getThermalZoneType(currThermalZone);
                thermalZoneTypes.append(currThermalZoneType).append(',');
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        StringBuilder cpuDevicesFreq = new StringBuilder();
        String gpuDeviceFreq = "gpuFrequency,";
        String cpuUtilization = "cpuUtilization,";
        String gpuUtilization = "gpuUtilization,";
        for (String currCPUDevice: cpuDevicePaths) {
            String[] cpuPathSplit = currCPUDevice.split("/");
            String currCPUDeviceName = cpuPathSplit[
                    cpuPathSplit.length - 2];
            cpuDevicesFreq.append(currCPUDeviceName).append("_freq,");
        }

        initialFreqTimes = new ArrayList<>();
        String cpuLittleFreqHeaders, cpuMidFreqHeaders, cpuBigFreqHeaders = "";
        try {
            cpuLittleFreqHeaders = getCPUPolicyHeaders(
                    "/sys/devices/system/cpu/cpufreq/policy0", "LITTLE");
            cpuMidFreqHeaders = getCPUPolicyHeaders(
                    "/sys/devices/system/cpu/cpufreq/policy4", "MID");
            cpuBigFreqHeaders = getCPUPolicyHeaders(
                    "/sys/devices/system/cpu/cpufreq/policy8", "BIG");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Create file for raw data collection
        String RAWFILEPATH = rawFilePath;
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(RAWFILEPATH, false))) {
            String sb = "time" +
                    ',' +
                    "relativeTime" +
                    ',' +
                    "thermalStatus" +
                    ',' +
                    thermalZoneTypes +
                    cpuDevicesFreq +
                    gpuDeviceFreq +
                    cpuUtilization +
                    gpuUtilization +
                    cpuLittleFreqHeaders +
                    cpuMidFreqHeaders +
                    cpuBigFreqHeaders +
                    '\n';
            writer.write(sb);
            System.out.println("Creating " + rawDataFileName + " done!");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

        dataCollection();
    }

    public void dataCollection() {
        Timer t = new Timer();
        t.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            processDataCollection();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                0, 2000);
    }

    public void processDataCollection() throws IOException {
        String FILEPATH = performanceFilePath;

        ArrayList<String> currentThermalData = processThermalData(false);
        ArrayList<Float> currentFrequencies = processFrequencyData();
        ArrayList<String> currentUtilizations = processUtilizationData();
        ArrayList<Float> currentPolicyTimes = processPolicyData();

        StringBuilder allThermalDataOfInterest = new StringBuilder();
        for (int i = 0; i < currentThermalData.size(); i++) {
            allThermalDataOfInterest.append(currentThermalData.get(i)).append(",");
        }

        dateFormat = new SimpleDateFormat("HH:mm:ss:SSS");
        String currTime = dateFormat.format(new Date());
        String relativeTime = Long.toString(getRelativeTime(currTime));
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, true))) {
            String sb = currTime +
                    ',' +
                    relativeTime +
                    ',' +
                    allThermalDataOfInterest +
                    currentFrequencies.get(0) +
                    ',' +
                    currentFrequencies.get(1) +
                    ',' +
                    currentUtilizations.get(0) +
                    ',' +
                    currentUtilizations.get(1) +
                    '\n';
            writer.write(sb);
            System.out.println("Writing to " + performanceFileName + " done!");
        } catch (FileNotFoundException | IndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        StringBuilder allCPUFreqs = new StringBuilder();
        for (int i = 2; i < currentFrequencies.size(); i++) {
            allCPUFreqs.append(currentFrequencies.get(i)).append(",");
        }

        currentThermalData = processThermalData(true);
        StringBuilder allThermalData = new StringBuilder();
        for (int i = 0; i < currentThermalData.size(); i++) {
            allThermalData.append(currentThermalData.get(i)).append(",");
        }

        StringBuilder formattedPolicyData = new StringBuilder();
        for (Float policyData: currentPolicyTimes) {
            formattedPolicyData.append(policyData).append(',');
        }
        formattedPolicyData.deleteCharAt(formattedPolicyData.length() - 1);

        String RAWFILEPATH = rawFilePath;
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(RAWFILEPATH, true))) {
            String sb = currTime +
                    ',' +
                    relativeTime +
                    ',' +
                    allThermalData +
                    allCPUFreqs +
                    currentFrequencies.get(1) +
                    ',' +
                    currentUtilizations.get(0) +
                    ',' +
                    currentUtilizations.get(1) +
                    ',' +
                    formattedPolicyData +
                    '\n';
            writer.write(sb);
            System.out.println("Writing to " + RAWFILEPATH + " done!");
        } catch (FileNotFoundException | IndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }
    }

    private ArrayList<String> processUtilizationData() {
        /*
        currentUtilizations = [cpuUtilization, gpuUtilization]
         */
        ArrayList<String> currentUtilizations = new ArrayList<>();

        // Get CPU Utilization
        String cpuUtilization = "0";
        try {
            cpuUtilization = getCPUUtilization();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentUtilizations.add(cpuUtilization);

        // Get GPU Utilization
        Float gpuUtilization = 0f;
        try {

            if (isRooted) {
                // For Pixel 8, ROOTED, the gpu frequency is stored in "/sys/class/misc/mali0/device/"
                // in file "cur_freq"
                gpuUtilization = getGPUUtilization(
                        "/sys/class/misc/mali0/device",
                        "utilization");
            } else {
                // For Note10+, the gpu utilization is stored in "/sys/class/kgsl/kgsl-3d0"
                // in file "busy_percentage"
                gpuUtilization = getGPUUtilization(
                        "/sys/class/kgsl/kgsl-3d0",
                        "gpu_busy_percentage");
            }


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentUtilizations.add(Float.toString(gpuUtilization));

        return currentUtilizations;
    }

    private ArrayList<Float> processFrequencyData() {
        /*
        currentFrequencies = [cpuFrequency, gpuFrequency, cpuFrequencies]
         */
        ArrayList<Float> currentFrequencies = new ArrayList<>();

        // Get CPU frequencies
        Float avgCPUFreq = 0f, gpuFreq = 0f;
        ArrayList<Float> cpuFreqs = new ArrayList<>();
        for (String cpuDevicePath: cpuDevicePaths) {
            try {
                Float currCPUFreq = getCPUFrequency(cpuDevicePath);
                cpuFreqs.add(currCPUFreq);
                avgCPUFreq += currCPUFreq;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        avgCPUFreq /= cpuDevicePaths.length;
        currentFrequencies.add(avgCPUFreq);

        // Get GPU Frequency
        try {
            if (isRooted) {
                // For Pixel 8, ROOTED, the gpu frequency is stored in "/sys/class/misc/mali0/device/"
                // in file "cur_freq"
                gpuFreq = getGPUFrequency("/sys/class/misc/mali0/device/", "cur_freq");
            } else {
                // For Note10+, the gpu frequency is stored in "/sys/class/kgsl/kgsl-3d0"
                // in file "cur_freq"
                gpuFreq = getGPUFrequency("/sys/class/kgsl/kgsl-3d0", "clock_mhz");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentFrequencies.add(gpuFreq);

        currentFrequencies.addAll(cpuFreqs);

        return currentFrequencies;
    }

    private ArrayList<String> processThermalData(Boolean isRaw) throws IOException {
        /*
        currentThermalData = [thermalStatus, cpuTemperature, gpuTemperature, npuTemperature, tpuTemperature]
        currentThermalData = [thermalStatus, devicesOfInterestTemperatures
         */

        ArrayList<String> currentThermalData= new ArrayList<>();

        String currentThermalStatus = "Unknown";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            currentThermalStatus = mainActivity.currentThermalStatus;
        }
        currentThermalData.add(currentThermalStatus);

        // Calculate Avg CPU, GPU, NPU, TPU temperatures
        Float avgCPUTemp = 0f, avgGPUTemp = 0f, avgNPUTemp = 0f, avgTPUTemp = 0f, currFileTemp = 0f;
        int cpuCount = 0, gpuCount = 0, npuCount = 0, tpuCount = 0;
        for (String zoneFilePath: thermalZonePaths) {
            currFileTemp = getThermalZoneTemp(zoneFilePath);
            if (isRooted) {
                currentThermalData.add(Float.toString(currFileTemp));
            } else {
                String currFileType = getThermalZoneType(zoneFilePath);
                for (String thermalZoneType: thermalZoneTypesOfInterest) {
                    if (currFileType.contains(thermalZoneType)) {
                        currentThermalData.add(Float.toString(currFileTemp));
                        // break out of for loop
                        break;
                    }
                }
            }
        }

        return currentThermalData;
    }

    private ArrayList<Float> processPolicyData() {
        /*
        currentPolicyTimes = [cpuLittlePolicyTime, cpuMidPolicyTime, cpuBigPolicyTime]
         */
        String policyPath = "/sys/devices/system/cpu/cpufreq/";
        String littlePath = policyPath + "policy0/stats/time_in_state";
        String midPath = policyPath + "policy4/stats/time_in_state";
        String bigPath = policyPath + "policy8/stats/time_in_state";
        ArrayList<Float> littleFreqTimes = null, midFreqTimes = null, bigFreqTimes = null;
        try {
            littleFreqTimes = getPolicyTimes(littlePath);
            midFreqTimes = getPolicyTimes(midPath);
            bigFreqTimes = getPolicyTimes(bigPath);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        ArrayList<Float> freqTimes = new ArrayList<>();
        freqTimes.addAll(littleFreqTimes);
        freqTimes.addAll(midFreqTimes);
        freqTimes.addAll(bigFreqTimes);

        ArrayList<Float> updatedTimes = new ArrayList<>();
        for (int i = 0; i < freqTimes.size(); i++) {
            updatedTimes.add(freqTimes.get(i) - initialFreqTimes.get(i));
        }

        return updatedTimes;
    }

    private String getCPUPolicyHeaders(String policyPath, String policyType) throws IOException, InterruptedException {
        String cmd = String.format("%s cat %s/stats/time_in_state", rootAccess, policyPath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String currLine;
        StringBuilder cpuPolicyHeaders = new StringBuilder();
        while ((currLine = reader.readLine()) != null) {
            String[] currLineSplit = currLine.split(" ");
            String currFreq = currLineSplit[0];
            Float currFreqHz = Float.parseFloat(currFreq) / 1000000f;
            String currTime = currLineSplit[1];
            Float initialTimeSecs = Float.parseFloat(currTime) / 1000f;
            initialFreqTimes.add(initialTimeSecs);
            cpuPolicyHeaders.append(currFreqHz).append(',');
        }
        initialFreqTimes.add(-1f);
        cpuPolicyHeaders.append(policyType).append(',');
        reader.close();
        process.waitFor();
        return cpuPolicyHeaders.toString();
    }

    private ArrayList<Float> getPolicyTimes(String policyPath) throws IOException, InterruptedException {
        String cmd = String.format("%s cat %s", rootAccess, policyPath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String currLine;
        ArrayList<Float> cpuPolicyFreqs = new ArrayList<>();
        while ((currLine = reader.readLine()) != null) {
            String[] currLineSplit = currLine.split(" ");
            String currTime = currLineSplit[1];
            Float currTimeSeconds = Float.parseFloat(currTime) / 1000f;
            cpuPolicyFreqs.add(currTimeSeconds);
        }
        cpuPolicyFreqs.add(-1f);
        reader.close();
        process.waitFor();
        return cpuPolicyFreqs;
    }

    private String[] getThermalZoneFilePaths(String thermalDir)
            throws IOException, InterruptedException {
        // thermalDir for Note10+ is usually "/sys/class/thermal"
        String cmd = String.format("%s ls %s", rootAccess, thermalDir);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String currFileName;
        ArrayList<String> thermalZonePaths = new ArrayList<>();
        while ((currFileName = reader.readLine()) != null) {
            if (currFileName.contains("thermal_zone")) {
                String thermalZoneFilePath = thermalDir + "/" + currFileName + "/";
                // Get all thermal zones and filter later
                thermalZonePaths.add(thermalZoneFilePath);
            }
        }

        reader.close();
        process.waitFor();
        return thermalZonePaths.toArray(new String[0]);
    }

    private String getThermalZoneType(String thermalZonePath) throws IOException {
        String cmd = String.format("%s cat %stype", rootAccess, thermalZonePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currLine = reader.readLine();
        if (currLine != null) {
            return currLine;
        }
        return "default_zone_type";
    }

    private Float getThermalZoneTemp(String thermalZonePath) throws IOException {
        String cmd = String.format("%s cat %stemp", rootAccess, thermalZonePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currTemp = reader.readLine();
        if (currTemp != null) {
            int tmpMCValue = Integer.parseInt(currTemp);
            if (tmpMCValue < 0) tmpMCValue = 0;
            return tmpMCValue / 1000f;
        }
        return (float) -1;
    }

    private String[] getCPUDeviceFiles(String cpuDeviceDirs)
            throws IOException, InterruptedException {
        // cpuDeviceDirs for Note10+ is usually "/sys/devices/system/cpu"
        String cmd = String.format("%s ls %s | grep 'cpu[0-9]'", rootAccess, cpuDeviceDirs);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currLine;
        List<String> cpuDevicePaths = new ArrayList<>();
        while ((currLine = reader.readLine()) != null) {
            if (currLine.matches("cpu[0-9]")) {
                String cpuDeviceFilePath = cpuDeviceDirs + "/" + currLine + "/";
                cpuDevicePaths.add(cpuDeviceFilePath);
            }
        }

        reader.close();
        process.waitFor();
        return cpuDevicePaths.toArray(new String[0]);
    }

    private Float getCPUFrequency(String cpuDevicePath) throws IOException {
        String cmd = String.format("%s cat %s/cpufreq/scaling_cur_freq", rootAccess, cpuDevicePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currFreq = reader.readLine();
        if (currFreq != null) {
            float tempFreq = Float.parseFloat(currFreq);
            tempFreq /= 1000000.0;
            return tempFreq;
        }
        return (float) 0;
    }

    private Float getGPUFrequency(String gpuDevicePath, String freqFileName) throws IOException {
        String cmd = String.format("%s cat %s/%s", rootAccess, gpuDevicePath, freqFileName);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String currentGPUFreq = reader.readLine();
        if (currentGPUFreq != null) {
            // convert to hz
            float mhzGPUFreq = Float.parseFloat(currentGPUFreq);
            return mhzGPUFreq / 1000;
        }
        return (float) 0;
    }

    private static String getCPUUtilization() throws IOException {
        String cmd = "top -s 6";
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String curr_cpu;
        while ((curr_cpu = reader.readLine()) != null) {
            if (curr_cpu.contains("org.tensorflow")) {
                while (curr_cpu.contains("  ")) {
                    curr_cpu = curr_cpu.replace("  ", " ");
                }
                curr_cpu = curr_cpu.replaceAll(" ", ",");
                break;
            }
        }
        List<String> cpu_util;
        if (curr_cpu != null) {
            cpu_util = Arrays.asList(curr_cpu.split(","));
        } else {
            cpu_util = Arrays.asList("-1", "-1", "-1", "-1");
        }
        return cpu_util.get(cpu_util.size() - 4);
    }

    private Float getGPUUtilization(String gpuDevicePath, String utilFileName) throws IOException {
        String cmd = String.format("%s cat %s/%s", rootAccess, gpuDevicePath, utilFileName);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String tempReader = reader.readLine();
        if (tempReader == null) return -1f;
        String[] currentUtilization = tempReader.split("%");
        String currentGPUUtilization = currentUtilization[0];

        if (currentGPUUtilization != null) {
            return Float.parseFloat(currentGPUUtilization);
        }
        return (float) 0;
    }

    private Long getRelativeTime(String currTime) {
        String[] timeStampSplit = currTime.split(":");
        long currTimeSecs = Long.parseLong(timeStampSplit[0]) * 3600 +
                Long.parseLong(timeStampSplit[1]) * 60 +
                Long.parseLong(timeStampSplit[2]);
        return currTimeSecs - startTimeSecs;
    }


}
