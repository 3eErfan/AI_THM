///*
// * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *             http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.tensorflow.lite.examples.imageclassification.fragments;
//
//import android.Manifest;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.widget.Toast;
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.core.content.ContextCompat;
//import androidx.fragment.app.Fragment;
//import androidx.navigation.Navigation;
//import org.tensorflow.lite.examples.imageclassification.R;
//
///** Handles requesting permissions for the device before using the camera */
//public class PermissionsFragment extends Fragment {
//
//    /**
//     * Convenience method used to check if all permissions required by this
//     * app are granted
//     */
//    public static boolean hasPermission(Context context) {
//        return ContextCompat.checkSelfPermission(context,
//                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
//    }
//
//    private final ActivityResultLauncher<String> requestPermissionLauncher
//            = registerForActivityResult(
//            new ActivityResultContracts.RequestPermission(), isGranted -> {
//                if (isGranted) {
//                    Toast.makeText(requireContext(),
//                                    "Permission request granted",
//                                    Toast.LENGTH_LONG)
//                            .show();
//                    navigateToCamera();
//                } else {
//                    Toast.makeText(requireContext(),
//                                    "Permission request denied",
//                                    Toast.LENGTH_LONG)
//                            .show();
//                }
//            });
//
//    @Override
//    public void onStart() {
//        super.onStart();
//        if (ContextCompat.checkSelfPermission(requireContext(),
//                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
//            navigateToCamera();
//        } else {
//            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
//        }
//    }
//
//    private void navigateToCamera() {
//        Navigation.findNavController(requireActivity(), R.id.fragment_container)
//                .navigate(PermissionsFragmentDirections.actionPermissionsToCamera());
//    }
//}


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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import org.tensorflow.lite.examples.imageclassification.R;

/** Handles requesting permissions for the device before using the camera */
public class PermissionsFragment extends Fragment {

    /**
     * Convenience method used to check if all permissions required by this
     * app are granted
     */
//    public static boolean hasPermission(Context context) {
//        return ContextCompat.checkSelfPermission(context,
//                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
//                && ContextCompat.checkSelfPermission(context,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
//                && ContextCompat.checkSelfPermission(context,
//                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
//                && ContextCompat.checkSelfPermission(context,
//                Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
//    }
    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q ||
                (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED));
    }



    private final ActivityResultLauncher<String[]> requestPermissionLauncher
            = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (
                        Boolean.TRUE.equals(isGranted.get(Manifest.permission.CAMERA))
                                && Boolean.TRUE.equals(isGranted.get(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                && Boolean.TRUE.equals(isGranted.get(Manifest.permission.READ_EXTERNAL_STORAGE))
                                && Boolean.TRUE.equals(isGranted.get(Manifest.permission.MANAGE_EXTERNAL_STORAGE))
                ) {
                    Toast.makeText(requireContext(),
                                    "Permission request granted",
                                    Toast.LENGTH_LONG)
                            .show();
                    navigateToCamera();
                } else {
                    Toast.makeText(requireContext(),
                                    "Permission request denied",
                                    Toast.LENGTH_LONG)
                            .show();
                }
            });

    @Override
    public void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        } else {
            if (!hasPermission(requireContext())) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                    requestPermissionLauncher.launch(
                            new String[]{
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                    );
                } else {
                    requestPermissionLauncher.launch(
                            new String[]{ Manifest.permission.CAMERA }
                    );
                }
            } else {
                navigateToCamera();
            }
        }
    }



    private void navigateToCamera() {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(PermissionsFragmentDirections.actionPermissionsToCamera());
    }
}
