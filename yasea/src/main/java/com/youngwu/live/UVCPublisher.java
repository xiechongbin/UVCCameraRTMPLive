package com.youngwu.live;

import android.media.AudioRecord;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;

import com.github.faucamp.simplertmp.RtmpHandler;
import com.serenegiant.usb.USBMonitor;
import com.seu.magicfilter.utils.MagicFilterType;

import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsEncoder;
import net.ossrs.yasea.SrsFlvMuxer;
import net.ossrs.yasea.SrsMp4Muxer;
import net.ossrs.yasea.SrsRecordHandler;

import java.io.File;

/**
 * Created by YoungWu on 19/7/5.
 */
public class UVCPublisher {
    private static AudioRecord mic;
    private static AcousticEchoCanceler aec;
    private static AutomaticGainControl agc;
    private byte[] mPcmBuffer = new byte[4096];
    private Thread aWorker;
    private UVCCameraView mUVVCCameraView;
    private boolean sendVideoOnly = false;
    private boolean sendAudioOnly = false;
    private int videoFrameCount;
    private long lastTimeMillis;
    private double mSamplingFps;

    private SrsFlvMuxer mFlvMuxer;
    private SrsMp4Muxer mMp4Muxer;
    private SrsEncoder mEncoder;

    public UVCPublisher(UVCCameraView view) {
        mUVVCCameraView = view;
        mUVVCCameraView.setPreviewCallback(new UVCCameraView.PreviewCallback() {
            @Override
            public void onGetRgbaFrame(byte[] data, int width, int height) {
                calcSamplingFps();
                if (!sendAudioOnly) {
                    mEncoder.onGetRgbaFrame(data, width, height);
                }
            }
        });
    }

    /**
     * Calculate sampling FPS
     */
    private void calcSamplingFps() {
        if (videoFrameCount == 0) {
            lastTimeMillis = System.nanoTime() / 1000000;
            videoFrameCount++;
        } else {
            if (++videoFrameCount >= SrsEncoder.vGOP) {
                long diffTimeMillis = System.nanoTime() / 1000000 - lastTimeMillis;
                mSamplingFps = (double) videoFrameCount * 1000 / diffTimeMillis;
                videoFrameCount = 0;
            }
        }
    }

    public void openCamera(USBMonitor.UsbControlBlock ctrlBlock) {
        mUVVCCameraView.openCamera(ctrlBlock);
    }

    public void closeCamera() {
        mUVVCCameraView.closeCamera();
    }

    public void startPreview() {
        mUVVCCameraView.startPreview();
    }

    public void stopPreview() {
        mUVVCCameraView.stopPreview();
    }

    public void startAudio() {
        mic = mEncoder.chooseAudioRecord();
        if (mic == null) {
            return;
        }

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(mic.getAudioSessionId());
            if (aec != null) {
                aec.setEnabled(true);
            }
        }

        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(mic.getAudioSessionId());
            if (agc != null) {
                agc.setEnabled(true);
            }
        }

        aWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                mic.startRecording();
                while (!Thread.interrupted()) {
                    if (sendVideoOnly) {
                        mEncoder.onGetPcmFrame(mPcmBuffer, mPcmBuffer.length);
                        try {
                            // This is trivial...
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            break;
                        }
                    } else {
                        if (mic != null) {
                            int size = mic.read(mPcmBuffer, 0, mPcmBuffer.length);
                            if (size > 0) {
                                mEncoder.onGetPcmFrame(mPcmBuffer, size);
                            }
                        }
                    }
                }
            }
        });
        aWorker.start();
    }

    public void stopAudio() {
        if (aWorker != null) {
            aWorker.interrupt();
            try {
                aWorker.join();
            } catch (InterruptedException e) {
                aWorker.interrupt();
            }
            aWorker = null;
        }

        if (mic != null) {
            mic.setRecordPositionUpdateListener(null);
            mic.stop();
            mic.release();
            mic = null;
        }

        if (aec != null) {
            aec.setEnabled(false);
            aec.release();
            aec = null;
        }

        if (agc != null) {
            agc.setEnabled(false);
            agc.release();
            agc = null;
        }
    }

    private void startEncode() {
        if (!mEncoder.start()) {
            return;
        }
        startPreview();
        startAudio();
        if (mEncoder != null && mEncoder.isEnabled()) {
            mUVVCCameraView.enableEncoding();
        }
    }

    private void resumeEncode() {
        startAudio();
        if (mEncoder != null && mEncoder.isEnabled()) {
            mUVVCCameraView.enableEncoding();
        }
    }

    private void pauseEncode() {
        stopAudio();
        mUVVCCameraView.disableEncoding();
    }

    private void stopEncode() {
        stopPreview();
        stopAudio();
        mEncoder.stop();
    }

    public void startPublish(String rtmpUrl) {
        if (mFlvMuxer != null) {
            mFlvMuxer.start(rtmpUrl);
            mFlvMuxer.setVideoResolution(mEncoder.getOutputWidth(), mEncoder.getOutputHeight());
            startEncode();
        }
    }

    public void resumePublish() {
        if (mFlvMuxer != null) {
            mEncoder.resume();
            resumeEncode();
        }
    }

    public void pausePublish() {
        if (mFlvMuxer != null) {
            mEncoder.pause();
            pauseEncode();
        }
    }

    public void stopPublish() {
        if (mFlvMuxer != null) {
            stopEncode();
            mFlvMuxer.stop();
        }
    }

    public boolean startRecord(String recPath) {
        return mMp4Muxer != null && mMp4Muxer.record(new File(recPath));
    }

    public void resumeRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.resume();
        }
    }

    public void pauseRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.pause();
        }
    }

    public void stopRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.stop();
        }
    }

    public boolean isAllFramesUploaded() {
        return mFlvMuxer.getVideoFrameCacheNumber().get() == 0;
    }

    public int getVideoFrameCacheCount() {
        if (mFlvMuxer != null) {
            return mFlvMuxer.getVideoFrameCacheNumber().get();
        }
        return 0;
    }

    public void switchToSoftEncoder() {
        mEncoder.switchToSoftEncoder();
    }

    public void switchToHardEncoder() {
        mEncoder.switchToHardEncoder();
    }

    public double getSamplingFps() {
        return mSamplingFps;
    }

    public void setPreviewResolution(int width, int height) {
        mUVVCCameraView.setPreviewResolution(width, height);
    }

    public void setOutputResolution(int width, int height) {
        if (width <= height) {
            mEncoder.setPortraitResolution(width, height);
        } else {
            mEncoder.setLandscapeResolution(width, height);
        }
    }

    public void setScreenOrientation(int orientation) {
        mEncoder.setScreenOrientation(orientation);
    }

    public void setVideoFullHDMode() {
        mEncoder.setVideoFullHDMode();
    }

    public void setVideoHDMode() {
        mEncoder.setVideoHDMode();
    }

    public void setVideoSmoothMode() {
        mEncoder.setVideoSmoothMode();
    }

    public void setSendVideoOnly(boolean flag) {
        if (mic != null) {
            if (flag) {
                mic.stop();
                mPcmBuffer = new byte[4096];
            } else {
                mic.startRecording();
            }
        }
        sendVideoOnly = flag;
    }

    public void setSendAudioOnly(boolean flag) {
        sendAudioOnly = flag;
    }

    public void switchCameraFilter(MagicFilterType type) {
        mUVVCCameraView.setFilter(type);
    }

    public void setRtmpHandler(RtmpHandler handler) {
        mFlvMuxer = new SrsFlvMuxer(handler);
        if (mEncoder != null) {
            mEncoder.setFlvMuxer(mFlvMuxer);
        }
    }

    public void setRecordHandler(SrsRecordHandler handler) {
        mMp4Muxer = new SrsMp4Muxer(handler);
        if (mEncoder != null) {
            mEncoder.setMp4Muxer(mMp4Muxer);
        }
    }

    public void setEncodeHandler(SrsEncodeHandler handler) {
        mEncoder = new SrsEncoder(handler);
        if (mFlvMuxer != null) {
            mEncoder.setFlvMuxer(mFlvMuxer);
        }
        if (mMp4Muxer != null) {
            mEncoder.setMp4Muxer(mMp4Muxer);
        }
    }

    public void setErrorCallback(UVCCameraView.ErrorCallback errorCallback) {
        mUVVCCameraView.setErrorCallback(errorCallback);
    }
}