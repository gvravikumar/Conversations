package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.DataBindingUtil;

import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;
import com.pedro.rtplibrary.util.FpsListener;

import net.ossrs.rtmp.ConnectCheckerRtmp;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityBroadcastBinding;
import eu.siacs.conversations.http.data.LiveStreamResponse;
import eu.siacs.conversations.services.ChannelDiscoveryService;

import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.writeGranted;

public class BroadcastActivity extends XmppActivity implements SurfaceHolder.Callback, ConnectCheckerRtmp, View.OnTouchListener, ChannelDiscoveryService.OnSecretKeyReceived {

    // Logging tag
    private static final String TAG = "MuxLive";

    // Mux's RTMP Entry point
    private static final String rtmpEndpoint = "rtmp://global-live.mux.com:5222/app/";

    // Config from the other activity comes in through an intent with some extra keys
    public static final String intentExtraStreamKey = "STREAMKEY";
    public static final String intentExtraPreset = "PRESET";
    public static final int REQUEST_CAMERA_AUDIO = 121;

    // UI Element references
    private TextView goLiveButton;
    private TextView bitrateLabel;
    private TextView fpsLabel;
    private SurfaceView surfaceView;

    private ActivityBroadcastBinding binding;

    private RtmpCamera1 rtmpCamera;
    private Boolean liveDesired = false;
    private String streamKey;
    private Preset preset;

    // Encoding presets and profiles
    public enum Preset {

        hd_1080p_30fps_5mbps(5000 * 1024, 1920, 1080, 30),
        hd_720p_30fps_3mbps(3000 * 1024, 1280, 720, 30),
        sd_540p_30fps_2mbps(2000 * 1024, 960, 540, 30),
        sd_360p_30fps_1mbps(1000 * 1024, 640, 360, 30),
        ;

        Preset(int bitrate, int width, int height, int frameRate) {
            this.bitrate = bitrate;
            this.height = height;
            this.width = width;
            this.frameRate = frameRate;
        }

        public int bitrate;
        public int height;
        public int width;
        public int frameRate;
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        if (hasCameraAndAudioPermission(REQUEST_CAMERA_AUDIO)) {
            binding.progressBar.setVisibility(View.VISIBLE);
            xmppConnectionService.getSecretKeyWithOnlyAudio(this);
        }
    }

    @Override
    public void secretKeyFound(LiveStreamResponse liveStreamResponse) {
        runOnUiThread(() -> {
            binding.progressBar.setVisibility(View.GONE);
//            muxToast(liveStreamResponse.get_skey());
            binding.button.setVisibility(View.VISIBLE);
            streamKey = liveStreamResponse.get_skey();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_broadcast);

        // Init the Surface View for the camera preview
        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);

        // Bind to labels and buttons
        goLiveButton = (TextView) findViewById(R.id.button);
        bitrateLabel = (TextView) findViewById(R.id.bitrateLabel);
        fpsLabel = (TextView) findViewById(R.id.fpslabel);

        // Setup the camera
        rtmpCamera = new RtmpCamera1(surfaceView, this);
        rtmpCamera.setReTries(1000); // Effectively retry forever

        // Listen for FPS change events to update the FPS indicator
        FpsListener.Callback callback = new FpsListener.Callback() {
            @Override
            public void onFps(int fps) {
                Log.i(TAG, "FPS: " + fps);
                runOnUiThread(new Runnable() {
                    public void run() {
                        fpsLabel.setText(fps + " fps");
                    }
                });
            }
        };
        rtmpCamera.setFpsListener(callback);

        // Set RTMP configuration from the intent that triggered this activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String streamKey = extras.getString(intentExtraStreamKey);
            Preset preset = (Preset) extras.getSerializable(intentExtraPreset);
            this.preset = preset;
            this.streamKey = streamKey;
            Log.i(TAG, "Stream Key: " + streamKey);
        }

        // Keep the screen active on the Broadcast Activity
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void goLiveClicked(View view) {
        Log.i(TAG, "Go Live Button tapped");

        if (liveDesired) {
            // Calling the "stopStream" function can take a while, so this happens on a new thread.
            goLiveButton.setText("Stopping...");
            new Thread(new Runnable() {
                public void run() {
                    rtmpCamera.stopStream();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            goLiveButton.setText("Go Live!");
                        }
                    });
                }
            }).start();
            liveDesired = false;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); // Unlock orientation
        } else {

            // Lock orientation to the current orientation while stream is active
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    break;
                case Surface.ROTATION_180:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                    break;
                case Surface.ROTATION_270:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                    break;
                default:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    break;
            }

            // Configure the stream using the configured preset
            rtmpCamera.prepareVideo(
                    preset.width,
                    preset.height,
                    preset.frameRate,
                    preset.bitrate,
                    2, // Fixed 2s keyframe interval
                    CameraHelper.getCameraOrientation(this)
            );
            rtmpCamera.prepareAudio(
                    128 * 1024, // 128kbps
                    48000, // 48k
                    true // Stereo
            );

            // Start the stream!
            rtmpCamera.startStream(rtmpEndpoint + streamKey);
            liveDesired = true;
            goLiveButton.setText("Connecting... (Cancel)");
        }
    }

    // Switches between the front and back camera
    public void changeCameraClicked(View view) {
        Log.i(TAG, "Change Camera Button tapped");
        rtmpCamera.switchCamera();
    }

    // Little wrapper to relocate and re-pad the toast a little
    private void muxToast(String message) {
        Toast t = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 5);
        t.show();
    }

    // Surfaceview Callbacks
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        // Stop the preview if it's running
        rtmpCamera.stopPreview();

        // Re-constrain the layout a little if the rotation of the application has changed
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        ConstraintLayout.LayoutParams l = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
        switch (rotation) {
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                l.dimensionRatio = "w,16:9";
                break;
            default:
                l.dimensionRatio = "h,9:16";
                break;
        }
        surfaceView.setLayoutParams(l);

        // Re-start the preview, which will also reset the rotation of the preview
        rtmpCamera.startPreview(1920, 1080);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
    }

    // RTMP Checker Callbacks
    @Override
    public void onConnectionSuccessRtmp() {
        goLiveButton.setText("Stop Streaming!");
        Log.i(TAG, "RTMP Connection Success");
        runOnUiThread(new Runnable() {
            public void run() {
                muxToast("RTMP Connection Successful!");
            }
        });
    }

    @Override
    public void onConnectionFailedRtmp(@NonNull String reason) {
        Log.w(TAG, "RTMP Connection Failure");
        runOnUiThread(new Runnable() {
            public void run() {
                goLiveButton.setText("Reconnecting... (Cancel)");
                Log.e(TAG, "run: " + reason);
                muxToast("RTMP Connection Failure: " + reason);
            }
        });

        // Retry RTMP connection failures every 5 seconds
        rtmpCamera.reTry(5000, reason);
    }

    @Override
    public void onNewBitrateRtmp(long bitrate) {
        Log.d(TAG, "RTMP Bitrate Changed: " + (bitrate / 1024));
        runOnUiThread(new Runnable() {
            public void run() {
                bitrateLabel.setText(bitrate / 1024 + " kbps");
            }
        });
    }

    @Override
    public void onDisconnectRtmp() {
        Log.i(TAG, "RTMP Disconnect");
        runOnUiThread(new Runnable() {
            public void run() {
                bitrateLabel.setText("0 kbps");
                fpsLabel.setText("0 fps");
                muxToast("RTMP Disconnected!");
            }
        });
    }

    // onAuthErrorRtmp and onAuthSuccessRtmp aren't used if you're using stream key based auth
    @Override
    public void onAuthErrorRtmp() {
    }

    @Override
    public void onAuthSuccessRtmp() {
    }

    // Touch Listener Callbacks
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                if (requestCode == REQUEST_CAMERA_AUDIO) {
                    onBackendConnected();
                }
            } else {
                Toast.makeText(this, R.string.no_camera_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
