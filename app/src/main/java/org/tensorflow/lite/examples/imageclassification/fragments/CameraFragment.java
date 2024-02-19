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
package org.tensorflow.lite.examples.imageclassification.fragments;

import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;

import org.tensorflow.lite.examples.imageclassification.ImageClassifierHelperKotlin;
import org.tensorflow.lite.examples.imageclassification.MainActivity;
import org.tensorflow.lite.examples.imageclassification.R;
import org.tensorflow.lite.examples.imageclassification.databinding.FragmentCameraBinding;
import org.tensorflow.lite.task.vision.classifier.Classifications;

/**
 * Fragment for displaying and controlling the device camera and other UI
 */
public class CameraFragment extends Fragment
        implements ImageClassifierHelperKotlin.ClassifierListener {

    private final String currentFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
    private static final String TAG = "Image Classifier";

    private FragmentCameraBinding fragmentCameraBinding;
    private BitmapUpdaterApi bitmapUpdaterApi;
    private DynamicBitmapSource source;
    private ImageClassifierHelperKotlin imageClassifierHelper;
    private ArrayList<ImageClassifierHelperKotlin> imageClassifierHelpers;
    private boolean imageClassifierStatus = false;
    private boolean testStatus = false;
    private Bitmap bitmapBuffer;
    private ClassificationResultAdapter classificationResultsAdapter;
    private ImageAnalysis imageAnalyzer;
    private ProcessCameraProvider cameraProvider;
    private final Object task = new Object();

    private SimpleDateFormat dateFormat;
    private String fileSeries;
    private final String throughputFileName = "Throughput_Measurements";
    private String experimet_time;
    private Timer t;
    private Long startTime;
    private Long testStartTime;
    private List<String> periodOptions;

    /**
     * Blocking camera operations are performed using this executor
     */
    private ExecutorService cameraExecutor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        fragmentCameraBinding = FragmentCameraBinding
                .inflate(inflater, container, false);
        return fragmentCameraBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!PermissionsFragment.hasPermission(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(
                            CameraFragmentDirections.actionCameraToPermissions()
                    );
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Shut down our background executor
        cameraExecutor.shutdown();
        synchronized (task) {
            imageClassifierHelper.clearImageClassifier();
            for (ImageClassifierHelperKotlin currClassifier : imageClassifierHelpers) {
                currClassifier.clearImageClassifier();
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity mainactivity = (MainActivity) getActivity();

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Get resources
        Resources res = getResources();
        periodOptions = Arrays.asList(res.getStringArray(R.array.period_spinner_options));

        // Set up BitmapUpdaterApi
        bitmapUpdaterApi = new BitmapUpdaterApi();

        // Set up DynamicBitmapSource
        source = new DynamicBitmapSource(bitmapUpdaterApi);

        imageClassifierHelper = new ImageClassifierHelperKotlin(
                requireContext(),
                this,
                source,
                0,
                0,
                periodOptions);
        imageClassifierHelpers = new ArrayList<>();
//        imageClassifierHelpers.add(imageClassifierHelper);

        // setup result adapter
        classificationResultsAdapter = new ClassificationResultAdapter();
        classificationResultsAdapter
                .updateAdapterSize(imageClassifierHelper.getMaxResults());
        fragmentCameraBinding.recyclerviewResults
                .setAdapter(classificationResultsAdapter);
        fragmentCameraBinding.recyclerviewResults
                .setLayoutManager(new LinearLayoutManager(requireContext()));


        // Set up the camera and its use cases
        fragmentCameraBinding.viewFinder.post(this::setUpCamera);

        // Attach listeners to UI control widgets
        initBottomSheetControls();

        dateFormat = new SimpleDateFormat("HH:mm:ss");
        fileSeries = dateFormat.format(new Date());
        String[] timeStampSplit = fileSeries.split(":");
        startTime = Long.parseLong(timeStampSplit[0]) * 3600 +
                Long.parseLong(timeStampSplit[1]) * 60 +
                Long.parseLong(timeStampSplit[2]);

        assert mainactivity != null;
        experimet_time =  mainactivity.get_exeriment_time();
        // Create file for data collection
        String FILEPATH = currentFolder + File.separator + throughputFileName + experimet_time + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, false))) {
            String sb = "time" +
                    ',' +
                    "relativeTime" +
                    ',' +
                    "modelIndex" +
                    ',' +
                    "model" +
                    ',' +
                    "delegate" +
                    ',' +
                    "throughput" +
                    ',' +
                    "avgThroughput" +
                    ',' +
                    "turnAroundTime" +
                    ',' +
                    "idleTime" +
                    ',' +
                    "avgMeasuredPeriod" +
                    ',' +
                    "measuredPeriod" +
                    ',' +
                    "targetPeriod" +
                    '\n';
            writer.write(sb);
            System.out.println("Creating " + throughputFileName + " done!");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        imageAnalyzer.setTargetRotation(
                fragmentCameraBinding.viewFinder.getDisplay().getRotation()
        );
    }

    private void initBottomSheetControls() {
        // When clicked, lower classification score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus
                .setOnClickListener(view -> {
                    float threshold = imageClassifierHelper.getThreshold();
                    if (threshold >= 0.1) {
                        imageClassifierHelper.setThreshold(threshold - 0.1f);
                        updateControlsUi();
                    }
                });

        // When clicked, raise classification score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus
                .setOnClickListener(view -> {
                    float threshold = imageClassifierHelper.getThreshold();
                    if (threshold < 0.9) {
                        imageClassifierHelper.setThreshold(threshold + 0.1f);
                        updateControlsUi();
                    }
                });

        // When clicked, reduce the number of objects that can be classified
        // at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus
                .setOnClickListener(view -> {
                    int maxResults = imageClassifierHelper.getMaxResults();
                    if (maxResults > 1) {
                        imageClassifierHelper.setMaxResults(maxResults - 1);
                        classificationResultsAdapter.updateAdapterSize(
                                imageClassifierHelper.getMaxResults()
                        );
                        updateControlsUi();
                    }
                });

        // When clicked, increase the number of objects that can be
        // classified at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus
                .setOnClickListener(view -> {
                    int maxResults = imageClassifierHelper.getMaxResults();
                    if (maxResults < 3) {
                        imageClassifierHelper.setMaxResults(maxResults + 1);
                        classificationResultsAdapter.updateAdapterSize(
                                imageClassifierHelper.getMaxResults()
                        );
                        updateControlsUi();
                    }
                });
        // When clicked, decrease the number of threads used for classification
        fragmentCameraBinding.bottomSheetLayout.threadsMinus
                .setOnClickListener(view -> {
                    int numThreads = imageClassifierHelper.getNumThreads();
                    if (numThreads > 1) {
                        imageClassifierHelper.setNumThreads(numThreads - 1);
                        updateControlsUi();
                    }
                });

        // When clicked, increase the number of threads used for classification
        fragmentCameraBinding.bottomSheetLayout.threadsPlus
                .setOnClickListener(view -> {
                    int numThreads = imageClassifierHelper.getNumThreads();
                    if (numThreads < 4) {
                        imageClassifierHelper.setNumThreads(numThreads + 1);
                        updateControlsUi();
                    }
                });

        // When clicked, change the underlying hardware used for inference.
        // Current options are CPU,GPU, and NNAPI
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate
                .setSelection(2, false);
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate
                .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView,
                                               View view,
                                               int position,
                                               long id) {
                        imageClassifierHelper.setCurrentDelegate(position);
                        updateControlsUi();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        // no-op
                    }
                });

        // When clicked, change the underlying model used for object
        // classification
        fragmentCameraBinding.bottomSheetLayout.spinnerModel
                .setSelection(0, false);
        fragmentCameraBinding.bottomSheetLayout.spinnerModel
                .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView,
                                               View view,
                                               int position,
                                               long id) {
//                        imageClassifierHelper.setCurrentModel(position);
                        updateControlsUi();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        // no-op
                    }
                });

        // When clicked, change the length of time between the execution of the next AI task
        fragmentCameraBinding.bottomSheetLayout.spinnerPeriod
                .setSelection(0, false);
        fragmentCameraBinding.bottomSheetLayout.spinnerPeriod
                .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView,
                                               View view,
                                               int position,
                                               long id) {
                        imageClassifierHelper.setCurrentPeriod(position);
                        updateControlsUi();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        // no-op
                    }
                });

        // When clicked, toggle the status of classification from active to
        // in-active and vice-versa.
        fragmentCameraBinding.bottomSheetLayout.stateToggleButton
                .setOnClickListener(view -> {
//                    imageClassifierStatus = !imageClassifierStatus;
//                    if (imageClassifierStatus) {
//                        testStartTime = SystemClock.uptimeMillis();
//                        configureImageClassifiers();
//                        source.startStream();
//                        runImageClassifiers();
//                        timedDataCollection();
//
//                    } else {
//                        synchronized (task) {
//                            t.cancel();
//                            pauseImageClassifiers();
//                            source.pauseStream();
//                        }
//                    }
//                    updateControlsUi();
                });

        // When clicked, configure all test models
        fragmentCameraBinding.bottomSheetLayout.testToggleButton
                .setOnClickListener(view -> {
                    testStatus = !testStatus;
                    if (!testStatus) {
                        synchronized (task) {
                            t.cancel();
                            pauseImageClassifiers();
                            source.pauseStream();
                        }
                        for (ImageClassifierHelperKotlin currClassifier : imageClassifierHelpers) {
                            currClassifier.clearImageClassifier();
                        }
                        imageClassifierHelpers.clear();
                    }else {
                        testStartTime = SystemClock.uptimeMillis();
                        configureImageClassifiers();
                        source.startStream();
                        runImageClassifiers();
                        timedDataCollection();
                    }
                    updateControlsUi();
                });
    }

    // Update the values displayed in the bottom sheet. Reset classifier.
    private void updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue
                .setText(String.valueOf(imageClassifierHelper.getMaxResults()));

        fragmentCameraBinding.bottomSheetLayout.thresholdValue
                .setText(String.format(Locale.US, "%.2f",
                        imageClassifierHelper.getThreshold()));

        fragmentCameraBinding.bottomSheetLayout.threadsValue
                .setText(String.valueOf(imageClassifierHelper.getNumThreads()));

        String modelStateText = getString(imageClassifierStatus ?
                (R.string.label_active) : (R.string.label_inactive));
        fragmentCameraBinding.bottomSheetLayout.stateToggleButton
                .setText(modelStateText);

        String testButtonText = getString(testStatus ?
                (R.string.label_active) : (R.string.label_inactive));
        fragmentCameraBinding.bottomSheetLayout.testToggleButton
                .setText(testButtonText);
        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when
        // applicable
        synchronized (task) {
            imageClassifierHelper.clearImageClassifier();
        }
    }




    // Initialize CameraX, and prepare to bind the camera use cases
    private void setUpCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Build and bind the camera use cases
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    // Declare and bind preview, capture and analysis use cases
    private void bindCameraUseCases() {
        // CameraSelector - makes assumption that we're only using the back
        // camera
        CameraSelector.Builder cameraSelectorBuilder = new CameraSelector.Builder();
        CameraSelector cameraSelector = cameraSelectorBuilder
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        // Preview. Only using the 4:3 ratio because this is the closest to
        // our model
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(
                        fragmentCameraBinding.viewFinder
                                .getDisplay().getRotation()
                )
                .build();

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.getDisplay().getRotation())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();


        // The analyzer can then be assigned to the instance
        imageAnalyzer.setAnalyzer(cameraExecutor, image -> {
            if (bitmapBuffer == null) {
                bitmapBuffer = Bitmap.createBitmap(
                        image.getWidth(),
                        image.getHeight(),
                        Bitmap.Config.ARGB_8888);
            }
            updateImage(image);
        });

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll();

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
            );

            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(
                    fragmentCameraBinding.viewFinder.getSurfaceProvider()
            );
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }

    private void updateImage(@NonNull ImageProxy image) {
        // Copy out RGB bits to the shared bitmap buffer
        bitmapBuffer.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

        int imageRotation = image.getImageInfo().getRotationDegrees();
        image.close();
        synchronized (task) {
            // Pass Bitmap and rotation to the image classifier helper for
            // processing and classification
            bitmapUpdaterApi.setLatestBitmap(bitmapBuffer);
            bitmapUpdaterApi.setLatestImageRotation(imageRotation);
        }
    }

    private void configureImageClassifiers() {
        imageClassifierHelpers.clear();
//        imageClassifierHelpers.add(imageClassifierHelper);
        if (testStatus) {
            ImageClassifierHelperKotlin classifier1 = new ImageClassifierHelperKotlin(
                    requireContext(),
                    this,
                    source,
                    0,
                    0,
                    periodOptions);
            ImageClassifierHelperKotlin classifier2 = new ImageClassifierHelperKotlin(
                    requireContext(),
                    this,
                    source,
                    1,
                    1,
                    periodOptions);
            ImageClassifierHelperKotlin classifier3 = new ImageClassifierHelperKotlin(
                    requireContext(),
                    this,
                    source,
                    2,
                    2,
                    periodOptions);
            ImageClassifierHelperKotlin Segmenter = new ImageClassifierHelperKotlin(
                    requireContext(),
                    this,
                    source,
                    4,
                    4,
                    periodOptions);

//            int mainICDelegate = imageClassifierHelper.getCurrentDelegateNum();
//            int mainICPeriod = imageClassifierHelper.getCurrentTaskPeriod();
//            String mainICDelegateName = imageClassifierHelper.getCurrentModel();

            classifier1.setCurrentDelegate(2);
            classifier1.setCurrentPeriod(11);

            classifier2.setCurrentDelegate(2);
            classifier2.setCurrentPeriod(11);

            classifier3.setCurrentDelegate(2);
            classifier3.setCurrentPeriod(11);

            Segmenter.setCurrentDelegate(2);
            Segmenter.setCurrentPeriod(11);

            imageClassifierHelpers.add(classifier1);
            imageClassifierHelpers.add(classifier2);
//            imageClassifierHelpers.add(classifier3);
            imageClassifierHelpers.add(Segmenter);
        }

    }

    private void runImageClassifiers() {
        for (ImageClassifierHelperKotlin currClassifier : imageClassifierHelpers) {
            currClassifier.startCollect();
        }
    }

    private void pauseImageClassifiers() {
        for (ImageClassifierHelperKotlin currClassifier : imageClassifierHelpers) {
            currClassifier.pauseCollect();
        }
    }

    private void timedDataCollection() {
        // Create a timer to periodically collect data
        t = new Timer();
        t.scheduleAtFixedRate(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        processDataCollection();
                    }
                },
                0,
                500
        );
    }

    @SuppressLint("SimpleDateFormat")
    private void processDataCollection() {
        long elapsedTimeMS = SystemClock.uptimeMillis() - testStartTime;
        long elapsedTimeS = elapsedTimeMS / 1000;
        long elapsedTimeMin = elapsedTimeS / 60;
        // Get current folder and path
        String FILEPATH = currentFolder + File.separator + throughputFileName + experimet_time + ".csv";

        for (ImageClassifierHelperKotlin currClassifier : imageClassifierHelpers) {
            long throughput = currClassifier.getCurrentThroughput();
            long avgThroughput = currClassifier.calculateAverageThroughput();
            long turnAroundTime = currClassifier.calculateAvgTAT();
            long period = currClassifier.getTaskPeriod();
            long averageMeasuredPeriod = currClassifier.getAvgMeasuredPeriod();
            long measuredPeriod = currClassifier.getMeasuredPeriod();
            long idleTime = max(0, period - turnAroundTime);

            // Write throughput to file
            try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, true))) {
                dateFormat = new SimpleDateFormat("HH:mm:ss:SSS");
                String currTime = dateFormat.format(new Date());
                String relativeTime = getRelativeTime(currTime);
                String sb = currTime +
                        ',' +
                        relativeTime +
                        ',' +
                        currClassifier.getIndex() +
                        ',' +
                        currClassifier.getCurrentModel() +
                        ',' +
                        currClassifier.getCurrentDelegate() +
                        ',' +
                        throughput +
                        ',' +
                        avgThroughput +
                        ',' +
                        currClassifier.getMeasuredTurnAround()+//turnAroundTime +
                        ',' +
                        idleTime +
                        ',' +
                        averageMeasuredPeriod +
                        ',' +
                        measuredPeriod +
                        ',' +
                        period +
                        '\n';
                writer.write(sb);
                System.out.println("Elapsed time(s):"+ elapsedTimeS +"  Writing to " + throughputFileName + " done! Models: " + imageClassifierHelpers.size());
            } catch (FileNotFoundException e) {
                System.out.println(e.getMessage());
            }

            if (elapsedTimeS>10*60 && elapsedTimeS<11*60 && currClassifier.getIndex()==0 && currClassifier.getCurrentTaskPeriod()==11){
                currClassifier.setCurrentPeriod(5);
            }

            if (elapsedTimeS>20*60 && currClassifier.getIndex()==0 && currClassifier.getCurrentTaskPeriod()==5){
                currClassifier.setCurrentPeriod(11);
            }

        }




        if (elapsedTimeMin > 30) {
            Button toggleButton = (Button) fragmentCameraBinding.bottomSheetLayout.testToggleButton;
            requireActivity().runOnUiThread(toggleButton::callOnClick);

        }


    }

    private String getRelativeTime(String currTime) {
        String[] timeValues = currTime.split(":");
        Long currTimeLong = Long.parseLong(timeValues[0]) * 3600 +
                Long.parseLong(timeValues[1]) * 60 +
                Long.parseLong(timeValues[2]) +
                Long.parseLong(timeValues[3]) / 1000;
        long relativeTime = currTimeLong - startTime;
        return Long.toString(relativeTime);
    }

    @Override
    public void onError(String error) {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            classificationResultsAdapter.updateResults(new ArrayList<>());
        });
    }

//    @Override
//    public void onResults(List<? extends Classifications> results, long inferenceTime, int modelIndex) {
//        requireActivity().runOnUiThread(() -> {
//            if (modelIndex == 0 & results != null) {
//                classificationResultsAdapter.updateResults(results.get(0).getCategories());
//                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal
//                        .setText(String.format(Locale.US, "%d ms", inferenceTime));
//            }
//        });
//    }

    @Override
    public void onResults( long inferenceTime, int modelIndex) {
        requireActivity().runOnUiThread(() -> {
            if (modelIndex == 0) {

            }
        });
    }

}
