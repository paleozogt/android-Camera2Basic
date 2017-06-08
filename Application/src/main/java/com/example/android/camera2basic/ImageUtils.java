package com.example.android.camera2basic;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.os.Build;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ImageUtils {
    public static void saveYuvImage(byte[] data, int width, int height, int imageRotation, File imageFile) {
        Bitmap bmp = null, bmpRotated = null;

        try {
            YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);

            // save the preview image
            FileOutputStream fs = new FileOutputStream(imageFile);
            image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, fs);
            fs.close();

            // add rotation tag
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(rotationDegreesToExifOrientation(imageRotation)));
            exif.saveAttributes();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (bmp != null) bmp.recycle();
            if (bmpRotated != null) bmpRotated.recycle();
        }
    }

    public static void saveJpegImage(byte[] data, File imageFile) {
        try {
            FileOutputStream fs = new FileOutputStream(imageFile);
            fs.write(data);
            fs.close();

            // correct for exif orientation
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotation = exifOrientationToRotationDegrees(exifOrientation);
            rotateImage(imageFile, rotation);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int exifOrientationToRotationDegrees(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    public static int rotationDegreesToExifOrientation(int rotationDegrees) {
        switch (rotationDegrees) {
            case 0:
                return ExifInterface.ORIENTATION_NORMAL;
            case 90:
                return ExifInterface.ORIENTATION_ROTATE_90;
            case 180:
                return ExifInterface.ORIENTATION_ROTATE_180;
            case 270:
                return ExifInterface.ORIENTATION_ROTATE_270;
            default:
                return ExifInterface.ORIENTATION_UNDEFINED;
        }
    }

    public static void rotateImage(File imageFile, int rotation) {
        Bitmap bmp = null, bmpRotated = null;
        if (rotation == 0) return;

        try {
            // rotate the image
            bmp = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            bmpRotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

            // save the rotated preview image
            FileOutputStream fs = new FileOutputStream(imageFile);
            bmpRotated.compress(Bitmap.CompressFormat.JPEG, 100, fs);
            fs.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (bmp != null) bmp.recycle();
            if (bmpRotated != null) bmpRotated.recycle();
        }
    }

    public static Bitmap decodeSampledBitmap(File file, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
