package com.example.android.camera2basic;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Build;

import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class ImageConverter {
    byte[] data;
    byte[] rowData;

    int width, height;
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public ImageConverter(Image image) {
        width= image.getWidth();
        height= image.getHeight();
        data = new byte[width * height * ImageFormat.getBitsPerPixel(image.getFormat()) / 8];
        rowData = new byte[getMaxRowSize(image)];
    }

    int getMaxRowSize(Image image) {
        Image.Plane[] planes = image.getPlanes();
        int maxRowSize = planes[0].getRowStride();
        for (int i = 0; i < planes.length; i++) {
            if (maxRowSize < planes[i].getRowStride()) {
                maxRowSize = planes[i].getRowStride();
            }
        }
        return maxRowSize;
    }

    public byte[] getBuffer() { return data; }

    /**
     * If the input image is YUV_420_888, will combine the planes together into a contiguous byte[]
     *
     * Adapted from http://androidxref.com/5.1.1_r6/xref/cts/apps/CtsVerifier/src/com/android/cts/verifier/camera/its/ItsUtils.java#119
     */
    public byte[] getDataFromImage(Image image) {
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();

        // Read image data
        Image.Plane[] planes = image.getPlanes();

        if (format == ImageFormat.YUV_420_888 || format == ImageFormat.RAW_SENSOR || format == ImageFormat.RAW10) {
            int offset = 0;
            for (int i = 0; i < planes.length; i++) {
                int w = (i == 0) ? width : width / 2;
                int h = (i == 0) ? height : height / 2;
                offset= copyPlane(planes[i], format, w, h, offset);
            }
            return data;
        } else {
            throw new RuntimeException("Unsupported image format: " + format);
        }
    }

    /**
     * If the input image is YUV_420_888, will output NV21.
     */
    public byte[] getInterleavedDataFromImage(Image image) {
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;

        // Read image data
        Image.Plane[] planes = image.getPlanes();

        if (format == ImageFormat.YUV_420_888 || format == ImageFormat.RAW_SENSOR || format == ImageFormat.RAW10) {
            int offset = 0, i= 0;
            Image.Plane yPlane= planes[i++];
            Image.Plane uPlane= planes[i++];
            Image.Plane vPlane= planes[i++];

            offset= copyPlane(yPlane, format, width, height, offset);
            copyPlaneInterleaved(vPlane, format, width/2, height/2, offset);
            copyPlaneInterleaved(uPlane, format, width/2, height/2, offset+bytesPerPixel);

            return data;
        } else {
            throw new RuntimeException("Unsupported image format: " + format);
        }
    }

    int copyPlane(Image.Plane plane, int format, int w, int h, int offset) {
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
        // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
        for (int row = 0; row < h; row++) {
            if (pixelStride == bytesPerPixel) {
                // Special case: optimized read of the entire row
                int length = w * bytesPerPixel;
                buffer.get(data, offset, length);
                // Advance buffer the remainder of the row stride
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
                offset += length;
            } else {
                // Generic case: should work for any pixelStride but slower.
                // Use intermediate buffer to avoid read byte-by-byte from
                // DirectByteBuffer, which is very bad for performance.
                // Also need avoid access out of bound by only reading the available
                // bytes in the bytebuffer.
                int readSize = rowStride;
                if (buffer.remaining() < readSize) {
                    readSize = buffer.remaining();
                }
                buffer.get(rowData, 0, readSize);
                if (pixelStride >= 1) {
                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                } else {
                    // PixelStride of 0 can mean pixel isn't a multiple of 8 bits, for
                    // example with RAW10. Just copy the buffer, dropping any padding at
                    // the end of the row.
                    int length = (w * ImageFormat.getBitsPerPixel(format)) / 8;
                    System.arraycopy(rowData, 0, data, offset, length);
                    offset += length;
                }
            }
        }
        return offset;
    }

    int copyPlaneInterleaved(Image.Plane plane, int format, int w, int h, int offset) {
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
        // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
        for (int row = 0; row < h; row++) {
            // Generic case: should work for any pixelStride but slower.
            // Use intermediate buffer to avoid read byte-by-byte from
            // DirectByteBuffer, which is very bad for performance.
            // Also need avoid access out of bound by only reading the available
            // bytes in the bytebuffer.
            int readSize = rowStride;
            if (buffer.remaining() < readSize) {
                readSize = buffer.remaining();
            }
            buffer.get(rowData, 0, readSize);
            if (pixelStride >= 1) {
                for (int col = 0; col < w; col++) {
                    data[offset] = rowData[col * pixelStride];
                    offset+= bytesPerPixel*2;
                }
            } else {
                // PixelStride of 0 can mean pixel isn't a multiple of 8 bits, for
                // example with RAW10.
                throw new RuntimeException("Unsupported pixel stride");
            }
        }
        return offset;
    }
}
