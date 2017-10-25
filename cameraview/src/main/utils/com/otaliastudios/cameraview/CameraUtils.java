package com.otaliastudios.cameraview;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Static utilities for dealing with camera I/O, orientation, etc.
 */
public class CameraUtils {


    /**
     * Determines whether the device has valid camera sensors, so the library
     * can be used.
     *
     * @param context a valid Context
     * @return whether device has cameras
     */
    public static boolean hasCameras(Context context) {
        PackageManager manager = context.getPackageManager();
        // There's also FEATURE_CAMERA_EXTERNAL , should we support it?
        return manager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || manager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }


    /**
     * Determines whether the device has a valid camera sensor with the given
     * Facing value, so that a session can be started.
     *
     * @param context a valid context
     * @param facing  either {@link Facing#BACK} or {@link Facing#FRONT}
     * @return true if such sensor exists
     */
    public static boolean hasCameraFacing(Context context, Facing facing) {
        int internal = new Mapper.Mapper1().map(facing);
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == internal) return true;
        }
        return false;
    }


    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     * This is executed in a background thread, and returns the result to the original thread.
     * <p>
     * This ignores flipping at the moment.
     * TODO care about flipping using Matrix.scale()
     *
     * @param source   a JPEG byte array
     * @param callback a callback to be notified
     */
    public static void decodeBitmap(final byte[] source, @Nullable final Float scale, final BitmapCallback callback) {
        if (scale != null && (scale < 0 || scale > 1.0f)) {
            throw new IllegalArgumentException("Scale needs to be between 0.0 and 1.0");
        }
        final Handler ui = new Handler();
        WorkerHandler.run(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decodeBitmap(source, scale);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onBitmapReady(bitmap);
                    }
                });
            }
        });
    }


    static Bitmap decodeBitmap(byte[] source, @Nullable final Float scale) {
        int orientation;
        boolean flip;
        InputStream stream = null;
        try {
            // http://sylvana.net/jpegcrop/exif_orientation.html
            stream = new ByteArrayInputStream(source);
            ExifInterface exif = new ExifInterface(stream);
            Integer exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    orientation = 0;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    orientation = 180;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_90:
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    orientation = 90;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    orientation = 270;
                    break;

                default:
                    orientation = 0;
            }

            flip = exifOrientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
                    exifOrientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
                    exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                    exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE;

        } catch (IOException e) {
            e.printStackTrace();
            orientation = 0;
            flip = false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(source, 0, source.length);
        Matrix matrix = new Matrix();

        if (scale != null) {
            matrix.setScale(scale, scale);
        }
        if (orientation != 0 || flip) {
            matrix.postRotate(orientation);
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    public interface BitmapCallback {
        @UiThread
        void onBitmapReady(Bitmap bitmap);
    }
}
