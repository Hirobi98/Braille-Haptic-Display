package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;

import okhttp3.*; // You will need to add implementation 'com.squareup.okhttp3:okhttp:4.12.0' to build.gradle
import org.json.JSONArray;
import org.json.JSONObject;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.content.Context;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BrailleHaptic";
    // Inside MainActivity class
    private final OkHttpClient httpClient = new OkHttpClient();
    // Replace this with the IP address printed in your ESP32 Serial Monitor
    // private static final String ESP32_URL = "[http://braille.local/send_array](http://braille.local/send_array)";

    private String esp32IpAddress = null;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;

    private ImageView imageView;
    private Button btnCapture, btnGallery;
    private TextView tvExtractedText, tvBrailleOutput, tvStatus;
    private LinearLayout brailleGridLayout;

    private Uri photoUri;
    private TextRecognizer recognizer;

    // ─── Activity Result Launchers ────────────────────────────────────────────
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && photoUri != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
                        imageView.setImageBitmap(bitmap);
                        runOCR(bitmap);
                    } catch (IOException e) {
                        tvStatus.setText("❌ Failed to load image: " + e.getMessage());
                    }
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        imageView.setImageBitmap(bitmap);
                        runOCR(bitmap);
                    } catch (IOException e) {
                        tvStatus.setText("❌ Failed to load image: " + e.getMessage());
                    }
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            });

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView        = findViewById(R.id.imageView);
        btnCapture       = findViewById(R.id.btnCapture);
        btnGallery       = findViewById(R.id.btnGallery);
        tvExtractedText  = findViewById(R.id.tvExtractedText);
        tvBrailleOutput  = findViewById(R.id.tvBrailleOutput);
        tvStatus         = findViewById(R.id.tvStatus);
        brailleGridLayout = findViewById(R.id.brailleGridLayout);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        btnCapture.setOnClickListener(v -> checkCameraPermission());
        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        initializeDiscoveryListener();
        startServiceDiscovery();
    }

    private void startServiceDiscovery() {
        // Look for HTTP services on the network
        nsdManager.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        tvStatus.setText("🔍 Searching for 'braille' device...");
    }

    private void initializeDiscoveryListener() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                // Check if the service name matches "braille"
                if (serviceInfo.getServiceName().contains("braille")) {
                    Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "Resolve failed: " + errorCode);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo resolvedServiceInfo) {
                            // GET THE IP!
                            esp32IpAddress = resolvedServiceInfo.getHost().getHostAddress();
                            Log.d(TAG, "Resolved IP: " + esp32IpAddress);

                            runOnUiThread(() -> tvStatus.setText("✅ Found ESP32 at: " + esp32IpAddress));
                        }
                    });
                }
            }

            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Discovery started");
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.e(TAG, "Service lost");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped");
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                nsdManager.stopServiceDiscovery(this);
            }
        };
    }
    // ─── Camera ───────────────────────────────────────────────────────────────
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        File photoFile = new File(getCacheDir(), "photo_" + System.currentTimeMillis() + ".jpg");
        photoUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
        cameraLauncher.launch(photoUri);
    }

    // ─── OCR ──────────────────────────────────────────────────────────────────
    private void runOCR(Bitmap bitmap) {
        tvStatus.setText("⏳ Running OCR...");
        tvExtractedText.setText("");
        tvBrailleOutput.setText("");
        brailleGridLayout.removeAllViews();

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String text = visionText.getText().trim();
                    if (text.isEmpty()) {
                        tvStatus.setText("❌ No text detected. Try a clearer image.");
                        return;
                    }
                    tvExtractedText.setText(text);
                    tvStatus.setText("✅ Text extracted! Converting to Braille...");
                    convertToBraille(text);
                })
                .addOnFailureListener(e -> {
                    tvStatus.setText("❌ OCR failed: " + e.getMessage());
                    Log.e(TAG, "OCR error", e);
                });
    }

    // ─── Braille Conversion ───────────────────────────────────────────────────


    private String matrixToString(boolean[][] matrix) {
        // 3 rows × 2 cols → "r0c0,r0c1 | r1c0,r1c1 | r2c0,r2c1"
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 3; r++) {
            if (r > 0) sb.append(" | ");
            sb.append(matrix[r][0] ? "1" : "0")
                    .append(",")
                    .append(matrix[r][1] ? "1" : "0");
        }
        return sb.toString();
    }

    private void addBrailleCell(char c, boolean[][] matrix) {
        View cell = LayoutInflater.from(this)
                .inflate(R.layout.braille_cell, brailleGridLayout, false);

        TextView cellChar = cell.findViewById(R.id.cellChar);
        cellChar.setText(String.valueOf(Character.toUpperCase(c)));

        // Dot IDs mapped to braille positions
        // Standard layout:  col0=left(dots1,2,3)  col1=right(dots4,5,6)
        //   row0 → dot1, dot4
        //   row1 → dot2, dot5
        //   row2 → dot3, dot6
        int[] dotIds = {
                R.id.dot1, R.id.dot4,   // row 0
                R.id.dot2, R.id.dot5,   // row 1
                R.id.dot3, R.id.dot6    // row 2
        };
        int[][] dotPositions = {
                {0, 0}, {0, 1},
                {1, 0}, {1, 1},
                {2, 0}, {2, 1}
        };

        for (int i = 0; i < dotIds.length; i++) {
            ImageView dot = cell.findViewById(dotIds[i]);
            int row = dotPositions[i][0];
            int col = dotPositions[i][1];
            dot.setBackgroundResource(
                    matrix[row][col] ? R.drawable.dot_active : R.drawable.dot_inactive
            );
        }

        brailleGridLayout.addView(cell);
    }
    private void sendBrailleToESP32(boolean[][] matrix) {
        if (esp32IpAddress == null) {
            runOnUiThread(() -> tvStatus.setText("❌ ESP32 not found on network yet."));
            return;
        }

        String targetUrl = "http://" + esp32IpAddress + "/send_array";

        try {
            JSONArray rootArray = new JSONArray();
            for (int r = 0; r < 3; r++) {
                JSONArray row = new JSONArray();
                row.put(matrix[r][0] ? 1 : 0); // col 0
                row.put(matrix[r][1] ? 1 : 0); // col 1
                rootArray.put(row);
            }

            JSONObject json = new JSONObject();
            json.put("array", rootArray);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(targetUrl)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "ESP32 Connection Failed", e);
                    // Add this to see the error on your phone screen
                    runOnUiThread(() -> tvStatus.setText("❌ ESP32 Error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "ESP32 Updated successfully");
                        // Add this to confirm success on your phone screen
                        runOnUiThread(() -> tvStatus.setText("✅ ESP32 Updated!"));
                    } else {
                        runOnUiThread(() -> tvStatus.setText("❌ ESP32 rejected data: " + response.code()));
                    }
                    response.close();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error formatting JSON", e);
        }
    }
    private void convertToBraille(String text) {
        brailleGridLayout.removeAllViews();

        for (char c : text.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != ' ') continue;

            boolean[][] matrix = BrailleConverter.charToMatrix(c);
            if (matrix == null) continue;

            addBrailleCell(c, matrix);
            sendBrailleToESP32(matrix);

            // --- ADD DELAY ---
            // Wait 2 seconds between characters so you have time to see/feel the dots
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }
    }

    @Override
    protected void onPause() {
        if (nsdManager != null && discoveryListener != null) {
            try { nsdManager.stopServiceDiscovery(discoveryListener); } catch (Exception e) {}
        }
        super.onPause();
    }
}
