package com.miumo.motion_detection;


import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Date; 


import com.miumo.motion_detection.R;
import com.miuno.motion_detection.detection.IMotionDetection;
import com.miuno.motion_detection.detection.RgbMotionDetection;
import com.miuno.motion_detection.image.ImageProcessing;

import android.app.Activity;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;




 
public class MotionDetectionActivity extends Activity {

    private static final String TAG = "MotionDetectionActivity";
    public MediaRecorder mrec = new MediaRecorder();    
    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private static boolean inPreview = false;
    private static IMotionDetection detector = null;
    public static MediaPlayer song;
    
    public static Vibrator mVibrator;

    private static volatile AtomicBoolean processing = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        song = MediaPlayer.create(this, R.raw.sound);
        mVibrator = (Vibrator)this.getSystemService(VIBRATOR_SERVICE);
       
        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

       
            detector = new RgbMotionDetection();
         
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        if(song!=null && song.isPlaying())
        {
    song.stop();}
        
        camera.setPreviewCallback(null);
        if (inPreview) camera.stopPreview();
        inPreview = false;
        camera.release();
        camera = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();

        camera = Camera.open();
    }

    private PreviewCallback previewCallback = new PreviewCallback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) return;
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;

          
                DetectionThread thread = new DetectionThread(data, size.width, size.height);
                thread.start();
            }
        
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = getBestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.startPreview();
            inPreview = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };
    
    
    //Video Recording 
    
    protected void startRecording() throws IOException
    {
        if(camera==null)
            camera = Camera.open();
        
         String filename;
         String path;
        
         path= Environment.getExternalStorageDirectory().getAbsolutePath().toString();
         
         Date date=new Date();
         filename="/rec"+date.toString().replace(" ", "_").replace(":", "_")+".mp4";
         
         //create empty file it must use
         File file=new File(path,filename);
         
        mrec = new MediaRecorder(); 

        camera.lock();
        camera.unlock();

        // Please maintain sequence of following code. 

        // If you change sequence it will not work.
        mrec.setCamera(camera);    
        mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mrec.setAudioSource(MediaRecorder.AudioSource.MIC);     
        mrec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mrec.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
        mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mrec.setPreviewDisplay(previewHolder.getSurface());
        mrec.setOutputFile(path+filename);
        mrec.prepare();
        mrec.start();

        
    }

    //Camera Preview
    private static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) result = size;
                }
            }
        }

        return result;
    }

    private static final class DetectionThread extends Thread {

        private byte[] data;
        private int width;
        private int height;

        public DetectionThread(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            if (!processing.compareAndSet(false, true)) return;

            // Log.d(TAG, "BEGIN PROCESSING...");
            try {
                
                // long bConversion = System.currentTimeMillis();
                int[] img = null;
                
                    img = ImageProcessing.decodeYUV420SPtoRGB(data, width, height);
                 
               
                // if motion Detected, start song
               
                if (img != null && detector.detect(img, width, height)) 
                {   
                	if(song!=null && !song.isPlaying())
                {              
                	song.start();
                	mVibrator.vibrate(50);
                	}           	           
                }
                else
                {  
                	if(song!=null && song.isPlaying())
                	{
                	song.pause();
                	
                	}
                	}
            } 
            catch (Exception e) {
                e.printStackTrace();
            } finally {
                processing.set(false);
            }
            // Log.d(TAG, "END PROCESSING...");

            processing.set(false);
        }
    };

 
}