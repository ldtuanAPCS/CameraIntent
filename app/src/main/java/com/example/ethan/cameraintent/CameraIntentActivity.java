package com.example.ethan.cameraintent;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.LruCache;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class CameraIntentActivity extends AppCompatActivity implements RecyclerViewClickPositionInterface {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private static final int ACTIVITY_START_CAMERA_APP = 0;
    private static final int REQUEST_EXTERNAL_STORAGE_RESULT = 1;
    private static final int REQUEST_CAMERA_RESULT = 2;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private static final int STATE_PICTURE_CAPTURED = 2;
    private static final String IMAGE_FILE_LOCATION = "image_file_location";
    private int mState;
    private static LruCache<String, Bitmap> mMemoryCache;
    private ImageView mCaptureView;
    private String mImageFileLocation;
    private String GALLERY_LOCATION = "image gallery";
    private File mGalleryFolder;
    private static File mImageFile;
    private RecyclerView mRecyclerView;
    private static Set<SoftReference<Bitmap>> mReuseableBitmap;
    private TextureView mTextureView;
    private Size mPreviewSize;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    private HandlerThread mBackroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private Activity mActivity;
    private static Uri mRequestingAppUri;

    private final Handler mUIHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            swapImageAdapter();
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(mActivity, reader.acquireNextImage(), mUIHandler));
        }
    };

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Toast.makeText(CameraIntentActivity.this, "Focus lock unsuccessful", Toast.LENGTH_SHORT).show();

        }

        private void process(CaptureResult result){
            switch (mState){
                case STATE_PREVIEW:
                    //Do nothing
                    break;
                case STATE_WAIT_LOCK:
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureRequest.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED){
                        mState = STATE_PICTURE_CAPTURED;
                        captureStillImage();
                    }
                    break;
            }
        }
    };

    @Override
    public void getRecyclerViewAdapterPosition(int position) {
        Intent sendFileAddressIntent = new Intent(this, SimpleImageActivity.class);
        sendFileAddressIntent.putExtra(IMAGE_FILE_LOCATION,
                sortFilesToLatest(mGalleryFolder)[position].toString());
        startActivity(sendFileAddressIntent);
    }

    private static class ImageSaver implements Runnable{
        private final Image mImage;
        private final Activity mActivity;
        private final Handler mHandler;

        private ImageSaver (Activity activity, Image image, Handler handler){
            mActivity = activity;
            mImage = image;
            mHandler = handler;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(mImageFile);
                fileOutputStream.write(bytes);
            } catch (IOException | NullPointerException e){
                e.printStackTrace();
            } finally {
                mImage.close();
                if (fileOutputStream != null){
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (mRequestingAppUri != null){
                    mRequestingAppUri = null;
                    mActivity.setResult(RESULT_OK);
                    mActivity.finish();
                }
                Message message = mHandler.obtainMessage();
                message.sendToTarget();
            }
        }
    }

    private CameraDevice.StateCallback mCameraDeviceStateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try{
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (mCameraDevice == null) return;
                    try{
                        mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingRequest(
                                mPreviewCaptureRequest,
                                mSessionCaptureCallback,
                                mBackgroundHandler
                        );
                    } catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(CameraIntentActivity.this, "Create camera session failed!", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            transformImage(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size largestImageSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth()*lhs.getHeight() - rhs.getWidth()*rhs.getHeight());
                            }
                        }
                );
                mImageReader = ImageReader.newInstance(largestImageSize.getWidth(),
                        largestImageSize.getHeight(),
                        ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height){
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes){
             if (width > height){
                 if (option.getWidth() > width & option.getHeight() > height) {
                     collectorSizes.add(option);
                 }
             } else {
                 if (option.getWidth() > height & option.getHeight() > width) {
                     collectorSizes.add(option);
                 }
             }
        }
        if (collectorSizes.size() > 0){
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth()*lhs.getHeight() - rhs.getWidth()*rhs.getHeight());
                }
            });
        }
        return  mapSizes[0];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_intent);
        createImageGallery();
        mRecyclerView = (RecyclerView) findViewById(R.id.galleryRecyclerView);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 1);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mRecyclerView.setLayoutManager(layoutManager);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED){
                RecyclerView.Adapter imageAdapter = new ImageAdapter(sortFilesToLatest(mGalleryFolder), this);
                mRecyclerView.setAdapter(imageAdapter);
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    Toast.makeText(this, "We need write permission to start the gallery and save images", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE_RESULT);
            }
        } else {
            RecyclerView.Adapter imageAdapter = new ImageAdapter(sortFilesToLatest(mGalleryFolder), this);
            mRecyclerView.setAdapter(imageAdapter);
        }

        final int maxMemorySize = (int) Runtime.getRuntime().maxMemory() / 1024;
        final int cacheSize = maxMemorySize / 20;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize){

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    mReuseableBitmap.add(new SoftReference<Bitmap>(oldValue));
                }
            }

            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            mReuseableBitmap = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
        }
        mTextureView = (TextureView) findViewById(R.id.textureView);

    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundThread();
        if (mTextureView.isAvailable()){
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            transformImage(mTextureView.getWidth(), mTextureView.getHeight());
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        closeBackgroundThread();
        super.onPause();
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null){
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null){
            mImageReader.close();
            mImageReader = null;
        }
    }

    public void takePhoto(View view){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                lockFocus();
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "External storage permission required to save images", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE_RESULT);
            }
        } else {
            lockFocus();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            switch (requestCode) {
                case REQUEST_CAMERA_RESULT:
                    if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Cannot run application because camera service permissions have not been granted", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case REQUEST_EXTERNAL_STORAGE_RESULT:
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        RecyclerView.Adapter imageAdapter = new ImageAdapter(sortFilesToLatest(mGalleryFolder), this);
                        mRecyclerView.setAdapter(imageAdapter);
                    } else {
                        Toast.makeText(this, "Don't have permission to start gallery or save images", Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_START_CAMERA_APP && resultCode == RESULT_OK){
            RecyclerView.Adapter newImageAdapter = new ImageAdapter(sortFilesToLatest(mGalleryFolder), this);
            mRecyclerView.swapAdapter(newImageAdapter, false);
        }
    }

    private void createImageGallery(){
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mGalleryFolder = new File(storageDirectory, GALLERY_LOCATION);
        if (!mGalleryFolder.exists()){
            mGalleryFolder.mkdirs();
        }
    }

    private File createImageFile() throws IOException{
        String timestamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
        String imageFileName = "IMAGE_" + timestamp + '_';
        File image = File.createTempFile(imageFileName, ".jpeg", mGalleryFolder);
        mImageFileLocation = image.getAbsolutePath();
        return image;
    }

    private File[] sortFilesToLatest(File fileImagesDir){
        File[] files = fileImagesDir.listFiles();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File lhs, File rhs) {
                    return Long.valueOf(rhs.lastModified()).compareTo(lhs.lastModified());
                }
            });
        }
        return files;
    }

    public static Bitmap getBitmapFromMemoryCache(String key){
        return mMemoryCache.get(key);
    }

    public static void setBitmapToMemoryCache(String key, Bitmap bitmap){
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    private static int getBytePerPixel(Bitmap.Config config){
        if (config == Bitmap.Config.ARGB_8888){
            return 4;
        } else if ((config == Bitmap.Config.RGB_565) || (config == Bitmap.Config.ARGB_4444)){
            return 2;
        } else if (config == Bitmap.Config.ALPHA_8){
            return 1;
        }
        return 1;
    }

    private static boolean canUseForBitmap(Bitmap candidate, BitmapFactory.Options options){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            int width = options.outWidth / options.inSampleSize;
            int height = options.outHeight / options.inSampleSize;
            int byteCount = width * height *getBytePerPixel(candidate.getConfig());
            return byteCount <= candidate.getAllocationByteCount();
        }
        return candidate.getWidth() == options.outWidth &&
                candidate.getHeight() == options.outHeight &&
                options.inSampleSize == 1;
    }

    public static Bitmap getBitmapFromReuseableSet(BitmapFactory.Options options){
        Bitmap bitmap = null;
        if (mReuseableBitmap != null && !mReuseableBitmap.isEmpty()){
            synchronized (mReuseableBitmap) {
                Bitmap item;
                Iterator<SoftReference<Bitmap>> iterator = mReuseableBitmap.iterator();
                while (iterator.hasNext()){
                    item = iterator.next().get();
                    if (item != null && item.isMutable()){
                        if (canUseForBitmap(item, options)){
                            bitmap = item;
                            iterator.remove();
                            break;
                        }
                    } else {
                        iterator.remove();
                    }
                }
            }
        }
        return bitmap;
    }

    private void openCamera(){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED){
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallBack, mBackgroundHandler);
                } else {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this, "No permission to use the camera services", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {Manifest.permission.CAMERA},
                            REQUEST_CAMERA_RESULT);
                }
            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallBack, mBackgroundHandler);
            }
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void openBackgroundThread(){
        mBackroundThread = new HandlerThread("Camera background thread");
        mBackroundThread.start();
        mBackgroundHandler = new Handler(mBackroundThread.getLooper());
    }

    private void closeBackgroundThread(){
        mBackroundThread.quitSafely();
        try{
            mBackroundThread.join();
            mBackroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    private void lockFocus(){
        try {
            mState = STATE_WAIT_LOCK;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(),
                    mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void unlockFocus(){
        try {
            mState = STATE_PREVIEW;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequestBuilder.build(),
                    mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void swapImageAdapter(){
        RecyclerView.Adapter newImageAdapter = new ImageAdapter(sortFilesToLatest(mGalleryFolder), this);
        mRecyclerView.swapAdapter(newImageAdapter, false);
    }

    private void captureStillImage(){
        try {
            CaptureRequest.Builder captureStillBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillBuilder.addTarget(mImageReader.getSurface());
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureStillBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    try{
                        mImageFile = createImageFile();
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    unlockFocus();
                }
            };
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(captureStillBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void transformImage (int width, int height){
        if (mPreviewSize == null || mTextureView == null){
            return;
        }
        Matrix matrix = new Matrix();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0,0,width,height);
        RectF previewRectF = new RectF(0,0,mPreviewSize.getHeight(),mPreviewSize.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270){
            previewRectF.offset(centerX - previewRectF.centerX(),
                    centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)width / mPreviewSize.getWidth(),
                    (float)height / mPreviewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation-2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }
}