package net.ossrs.yasea;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Leo Ma on 4/1/2016.
 */
public class SrsEncoder {
    private static final boolean DEBUG = false;
    private static final String TAG = "SrsEncoder";

    public static final String vCodec = SrsLiveConfig.VIDEO_CODEC;
    public static final String aCodec = SrsLiveConfig.AUDIO_CODEC;
    public static String x264Preset = SrsLiveConfig.XH264_VERY_FAST_PRESET;

    public static int vPortraitWidth = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;
    public static int vPortraitHeight = SrsLiveConfig.HIGH_DEFINITION_WIDTH;
    public static int vLandscapeWidth = SrsLiveConfig.HIGH_DEFINITION_WIDTH;
    public static int vLandscapeHeight = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;
    public static int vOutWidth = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;   // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
    public static int vOutHeight = SrsLiveConfig.HIGH_DEFINITION_WIDTH;  // Since Y component is quadruple size as U and V component, the stride must be set as 32x

    public static int vBitrate = SrsLiveConfig.HIGH_DEFINITION_BITRATE;//视频比特率
    public static int vFPS = SrsLiveConfig.NORMAL_FPS;//视频帧率
    public static final int vGOP = SrsLiveConfig.GOP;//I帧间隔周期

    public static int aBitrate = SrsLiveConfig.HIGH_QUALITY_BITRATE;//音频比特率
    public static final int aSampleRate = SrsLiveConfig.AUDIO_SAMPLERATE;//音频采样率
    public static int aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;//立体声通道

    private SrsEncodeHandler mHandler;

    private SrsFlvMuxer flvMuxer;
    private SrsMp4Muxer mp4Muxer;

    private MediaCodecInfo vmci;
    private MediaCodec vencoder;
    private MediaCodec aencoder;

    private boolean networkWeakTriggered = false;
    private boolean useSoftEncoder = false;
    private boolean canSoftEncode = false;

    private long mPresentTimeUs;
    private long mPausetime;

    private int mVideoColorFormat;

    private int videoFlvTrack;
    private int videoMp4Track;
    private int audioFlvTrack;
    private int audioMp4Track;

    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv

    public SrsEncoder(SrsEncodeHandler handler) {
        mHandler = handler;
        mVideoColorFormat = chooseVideoEncoder();
    }

    public void setFlvMuxer(SrsFlvMuxer flvMuxer) {
        this.flvMuxer = flvMuxer;
    }

    public void setMp4Muxer(SrsMp4Muxer mp4Muxer) {
        this.mp4Muxer = mp4Muxer;
    }

    public boolean start() {
        if (flvMuxer == null || mp4Muxer == null) {
            return false;
        }

        // the referent PTS for video and audio encoder.
        mPresentTimeUs = System.nanoTime() / 1000;

        // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
        // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        if (!useSoftEncoder && (vOutWidth % 32 != 0 || vOutHeight % 32 != 0)) {
            if (vmci.getName().contains("MTK")) {
                throw new AssertionError("MTK encoding revolution stride must be 32x");
            }
        }

        setEncoderResolution(vOutWidth, vOutHeight);
        setEncoderFps(vFPS);
        setEncoderGop(vGOP);
        // Unfortunately for some android phone, the output fps is less than 10 limited by the
        // capacity of poor cheap chips even with x264. So for the sake of quick appearance of
        // the first picture on the player, a spare lower GOP value is suggested. But note that
        // lower GOP will produce more I frames and therefore more streaming data flow.
        // setEncoderGop(15);
        setEncoderBitrate(vBitrate);
        setEncoderPreset(x264Preset);

        if (useSoftEncoder) {
            canSoftEncode = openSoftEncoder();
            if (!canSoftEncode) {
                return false;
            }
        }

        // aencoder pcm to aac raw stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            aencoder = MediaCodec.createEncoderByType(aCodec);
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "create aencoder failed.");
            e.printStackTrace();
            return false;
        }

        // setup the aencoder.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        int ach = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(aCodec, aSampleRate, ach);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, aBitrate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        aencoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the audio tracker to muxer.
        audioFlvTrack = flvMuxer.addTrack(audioFormat);
        audioMp4Track = mp4Muxer.addTrack(audioFormat);

        // vencoder yuv to 264 es stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            vencoder = MediaCodec.createByCodecName(vmci.getName());
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "create vencoder failed.");
            e.printStackTrace();
            return false;
        }

        // setup the vencoder.
        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        MediaFormat videoFormat = MediaFormat.createVideoFormat(vCodec, vOutWidth, vOutHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, vBitrate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, vFPS);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, vGOP);
        vencoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the video tracker to muxer.
        videoFlvTrack = flvMuxer.addTrack(videoFormat);
        videoMp4Track = mp4Muxer.addTrack(videoFormat);

        // start device and encoder.
        vencoder.start();
        aencoder.start();
        return true;
    }

    public void pause() {
        mPausetime = System.nanoTime() / 1000;
    }

    public void resume() {
        long resumeTime = (System.nanoTime() / 1000) - mPausetime;
        mPresentTimeUs = mPresentTimeUs + resumeTime;
        mPausetime = 0;
    }

    public void stop() {
        if (useSoftEncoder) {
            closeSoftEncoder();
            canSoftEncode = false;
        }

        if (aencoder != null) {
            if (DEBUG) Log.i(TAG, "stop aencoder");
            try {
                aencoder.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            aencoder.release();
            aencoder = null;
        }

        if (vencoder != null) {
            if (DEBUG) Log.i(TAG, "stop vencoder");
            try {
                vencoder.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            vencoder.release();
            vencoder = null;
        }
    }

    public void switchToSoftEncoder() {
        useSoftEncoder = true;
    }

    public void switchToHardEncoder() {
        useSoftEncoder = false;
    }

    public boolean isSoftEncoder() {
        return useSoftEncoder;
    }

    public boolean canHardEncode() {
        return vencoder != null;
    }

    public boolean canSoftEncode() {
        return canSoftEncode;
    }

    public boolean isEnabled() {
        return canHardEncode() || canSoftEncode();
    }

    public void setPortraitResolution(int width, int height) {
        vOutWidth = width;
        vOutHeight = height;
        vPortraitWidth = width;
        vPortraitHeight = height;
        vLandscapeWidth = height;
        vLandscapeHeight = width;
    }

    public void setLandscapeResolution(int width, int height) {
        vOutWidth = width;
        vOutHeight = height;
        vLandscapeWidth = width;
        vLandscapeHeight = height;
        vPortraitWidth = height;
        vPortraitHeight = width;
    }

    public void setVideoFullHDMode() {
        vBitrate = SrsLiveConfig.FULL_HIGH_DEFINITION_BITRATE;
        aBitrate = SrsLiveConfig.APE_FLAC_BITRATE;
        vFPS = SrsLiveConfig.HIGH_FPS;
        x264Preset = SrsLiveConfig.XH264_VERY_FAST_PRESET;
    }

    public void setVideoHDMode() {
        vBitrate = SrsLiveConfig.HIGH_DEFINITION_BITRATE;
        aBitrate = SrsLiveConfig.HIGH_QUALITY_BITRATE;
        vFPS = SrsLiveConfig.NORMAL_FPS;
        x264Preset = SrsLiveConfig.XH264_VERY_FAST_PRESET;
    }

    public void setVideoSmoothMode() {
        vBitrate = SrsLiveConfig.STANDARD_DEFINITION_BITRATE;
        aBitrate = SrsLiveConfig.NORMAL_QUALITY_BITRATE;
        vFPS = SrsLiveConfig.POOR_FPS;
        x264Preset = SrsLiveConfig.XH264_SUPER_FAST_PRESET;
    }

    public int getOutputWidth() {
        return vOutWidth;
    }

    public int getOutputHeight() {
        return vOutHeight;
    }

    public void setScreenOrientation(int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            vOutWidth = vPortraitWidth;
            vOutHeight = vPortraitHeight;
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            vOutWidth = vLandscapeWidth;
            vOutHeight = vLandscapeHeight;
        }

        // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
        // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        if (!useSoftEncoder && (vOutWidth % 32 != 0 || vOutHeight % 32 != 0)) {
            if (vmci.getName().contains("MTK")) {
                throw new AssertionError("MTK encoding revolution stride must be 32x");
            }
        }

        setEncoderResolution(vOutWidth, vOutHeight);
    }

    private void onProcessedYuvFrame(byte[] yuvFrame, long pts) {
        ByteBuffer[] inBuffers = vencoder.getInputBuffers();
        ByteBuffer[] outBuffers = vencoder.getOutputBuffers();

        int inBufferIndex = vencoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(yuvFrame, 0, yuvFrame.length);
            vencoder.queueInputBuffer(inBufferIndex, 0, yuvFrame.length, pts, 0);
        }

        for (; ; ) {
            MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();
            int outBufferIndex = vencoder.dequeueOutputBuffer(vebi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                onEncodedAnnexbFrame(bb, vebi);
                vencoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    private void onSoftEncodedData(byte[] es, long pts, boolean isKeyFrame) {
        ByteBuffer bb = ByteBuffer.wrap(es);
        MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();
        vebi.offset = 0;
        vebi.size = es.length;
        vebi.presentationTimeUs = pts;
        vebi.flags = isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
        onEncodedAnnexbFrame(bb, vebi);
    }

    // when got encoded h264 es stream.
    private void onEncodedAnnexbFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        mp4Muxer.writeSampleData(videoMp4Track, es.duplicate(), bi);
        flvMuxer.writeSampleData(videoFlvTrack, es, bi);
    }

    // when got encoded aac raw stream.
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        mp4Muxer.writeSampleData(audioMp4Track, es.duplicate(), bi);
        flvMuxer.writeSampleData(audioFlvTrack, es, bi);
    }

    public void onGetPcmFrame(byte[] data, int size) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            ByteBuffer[] inBuffers = aencoder.getInputBuffers();
            ByteBuffer[] outBuffers = aencoder.getOutputBuffers();

            int inBufferIndex = aencoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                bb.put(data, 0, size);
                long pts = System.nanoTime() / 1000 - mPresentTimeUs;
                aencoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
            }

            for (; ; ) {
                MediaCodec.BufferInfo aebi = new MediaCodec.BufferInfo();
                int outBufferIndex = aencoder.dequeueOutputBuffer(aebi, 0);
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = outBuffers[outBufferIndex];
                    onEncodedAacFrame(bb, aebi);
                    aencoder.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }
        }
    }

    public void onGetRgbaFrame(byte[] data, int width, int height) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            if (useSoftEncoder) {
                swRgbaFrame(data, width, height, pts);
            } else {
                byte[] processedData = hwRgbaFrame(data, width, height);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    public void onGetYuvFrame(byte[] data) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;

            onProcessedYuvFrame(data, pts);

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    public void onGetYuvNV21Frame(byte[] data, int width, int height, Rect boundingBox) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            if (useSoftEncoder) {
                throw new UnsupportedOperationException("Not implemented");
                //swRgbaFrame(data, width, height, pts);
            } else {
                byte[] processedData = hwYUVNV21FrameScaled(data, width, height, boundingBox);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    public void onGetArgbFrame(int[] data, int width, int height, Rect boundingBox) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            if (useSoftEncoder) {
                throw new UnsupportedOperationException("Not implemented");
                //swArgbFrame(data, width, height, pts);
            } else {
                byte[] processedData = hwArgbFrameScaled(data, width, height, boundingBox);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    public void onGetArgbFrame(int[] data, int width, int height) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            if (useSoftEncoder) {
                throw new UnsupportedOperationException("Not implemented");
                //swArgbFrame(data, width, height, pts);
            } else {
                byte[] processedData = hwArgbFrame(data, width, height);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    private byte[] hwRgbaFrame(byte[] data, int width, int height) {
        switch (mVideoColorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                return RGBAToI420(data, width, height, true, 180);
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return RGBAToNV12(data, width, height, true, 180);
            default:
                throw new IllegalStateException("Unsupported color format!");
        }
    }

    private byte[] hwYUVNV21FrameScaled(byte[] data, int width, int height, Rect boundingBox) {
        switch (mVideoColorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                return NV21ToI420Scaled(data, width, height, true, 180, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return NV21ToNV12Scaled(data, width, height, true, 180, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
            default:
                throw new IllegalStateException("Unsupported color format!");
        }
    }

    private byte[] hwArgbFrameScaled(int[] data, int width, int height, Rect boundingBox) {
        switch (mVideoColorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                return ARGBToI420Scaled(data, width, height, false, 0, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return ARGBToNV12Scaled(data, width, height, false, 0, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
            default:
                throw new IllegalStateException("Unsupported color format!");
        }
    }

    private byte[] hwArgbFrame(int[] data, int inputWidth, int inputHeight) {
        switch (mVideoColorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                return ARGBToI420(data, inputWidth, inputHeight, false, 0);
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return ARGBToNV12(data, inputWidth, inputHeight, false, 0);
            default:
                throw new IllegalStateException("Unsupported color format!");
        }
    }

    private void swRgbaFrame(byte[] data, int width, int height, long pts) {
        RGBASoftEncode(data, width, height, true, 180, pts);
    }

    public AudioRecord chooseAudioRecord() {
        AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SrsEncoder.aSampleRate,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, getPcmBufferSize() * 4);
        if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
            mic = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SrsEncoder.aSampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, getPcmBufferSize() * 4);
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
                mic = null;
            } else {
                SrsEncoder.aChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            }
        } else {
            SrsEncoder.aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
        }

        return mic;
    }

    private int getPcmBufferSize() {
        int pcmBufSize = AudioRecord.getMinBufferSize(aSampleRate, AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) + 8191;
        return pcmBufSize - (pcmBufSize % 8192);
    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String name) {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(vCodec)) {
                    if (DEBUG)
                        Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return null;
    }

    // choose the right supported color format. @see below:
    private int chooseVideoEncoder() {
        // choose the encoder "video/avc":
        //      1. select default one when type matched.
        //      2. google avc is unusable.
        //      3. choose qcom avc.
        vmci = chooseVideoEncoder(null);
        //vmci = chooseVideoEncoder("google");
        //vmci = chooseVideoEncoder("qcom");

        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = vmci.getCapabilitiesForType(vCodec);
        for (int i = 0; i < cc.colorFormats.length; i++) {
            int cf = cc.colorFormats[i];
            if (DEBUG)
                Log.i(TAG, String.format("vencoder %s supports color fomart 0x%x(%d)", vmci.getName(), cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            if (cf >= cc.COLOR_FormatYUV420Planar && cf <= cc.COLOR_FormatYUV420SemiPlanar) {
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }
        }

        for (int i = 0; i < cc.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            if (DEBUG)
                Log.i(TAG, String.format("vencoder %s support profile %d, level %d", vmci.getName(), pl.profile, pl.level));
        }

        if (DEBUG)
            Log.i(TAG, String.format("vencoder %s choose color format 0x%x(%d)", vmci.getName(), matchedColorFormat, matchedColorFormat));
        return matchedColorFormat;
    }

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    private static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    private static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

    private native void setEncoderResolution(int outWidth, int outHeight);

    private native void setEncoderFps(int fps);

    private native void setEncoderGop(int gop);

    private native void setEncoderBitrate(int bitrate);

    private native void setEncoderPreset(String preset);

    private native byte[] RGBAToI420(byte[] rgbaFrame, int width, int height, boolean flip,
                                     int rotate);

    private native byte[] RGBAToNV12(byte[] rgbaFrame, int width, int height, boolean flip,
                                     int rotate);

    private native byte[] ARGBToI420Scaled(int[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native byte[] ARGBToNV12Scaled(int[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native byte[] ARGBToI420(int[] frame, int src_width, int src_height,
                                     boolean need_flip, int rotate_degree);

    private native byte[] ARGBToNV12(int[] frame, int src_width, int src_height,
                                     boolean need_flip, int rotate_degree);

    private native byte[] NV21ToNV12Scaled(byte[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native byte[] NV21ToI420Scaled(byte[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native int RGBASoftEncode(byte[] rgbaFrame, int width, int height, boolean flip,
                                      int rotate, long pts);

    private native boolean openSoftEncoder();

    private native void closeSoftEncoder();

    static {
        System.loadLibrary("yuv");
        System.loadLibrary("enc");
    }
}