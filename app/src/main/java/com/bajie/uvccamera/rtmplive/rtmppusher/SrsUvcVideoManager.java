package com.bajie.uvccamera.rtmplive.rtmppusher;

import android.util.Log;

import com.github.faucamp.simplertmp.RtmpHandler;

import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsRecordHandler;
import net.ossrs.yasea.SrsUvcPublisher;

import java.io.IOException;
import java.net.SocketException;

/**
 * Created by YoungWu on 19/7/5.
 */
public class SrsUvcVideoManager implements SrsEncodeHandler.SrsEncodeListener, RtmpHandler.RtmpListener, SrsRecordHandler.SrsRecordListener {
    private static final boolean DEBUG = true;
    private static final String TAG = "SrsUvcVideoManager";
    private SrsUvcPublisher mPublisher;

    /**
     * 初始化视频直播
     *
     * @param width  分辨率－宽
     * @param height 分辨率－高
     */
    public SrsUvcVideoManager(int width, int height) {
        mPublisher = new SrsUvcPublisher();
        //编码状态回调
        mPublisher.setEncodeHandler(new SrsEncodeHandler(this));
        mPublisher.setRecordHandler(new SrsRecordHandler(this));
        //rtmp推流状态回调
        mPublisher.setRtmpHandler(new RtmpHandler(this));
        //预览分辨率
        mPublisher.setPreviewResolution(width, height);
        //推流分辨率
        mPublisher.setOutputResolution(width, height);
        //传输率
        mPublisher.setVideoHDMode();
        //只传输视频数据
        mPublisher.setSendVideoOnly(true);
    }

    /**
     * 结束推流
     */
    public void stopPublish() {
        mPublisher.stopPublish();
        if (DEBUG) Log.e(TAG, "结束推流");
    }

    /**
     * 开始推流
     *
     * @param url 推流地址
     */
    public void startPublish(String url) {
        if (DEBUG) Log.e(TAG, "开始推流：" + url);
        mPublisher.startPublish(url);
    }

    /**
     * 填充帧数据
     *
     * @param yuv 帧数据
     */
    public void pushYUV(byte[] yuv) {
        mPublisher.setVideoYuvData(yuv);
    }

    /**
     * 处理异常
     *
     * @param e 异常情况
     */
    private void handleException(Exception e) {
        if (e != null) {
            if (DEBUG) Log.e(TAG, "视频直播出现异常");
        }

    }

    @Override
    public void onNetworkWeak() {
        if (DEBUG) Log.e(TAG, "网络状况差");
    }

    @Override
    public void onNetworkResume() {
        if (DEBUG) Log.e(TAG, "网络恢复");
    }

    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    @Override
    public void onRtmpConnecting(String msg) {
        if (DEBUG) Log.e(TAG, "onRtmpConnecting:" + msg);
    }

    @Override
    public void onRtmpConnected(String msg) {
        if (DEBUG) Log.e(TAG, "onRtmpConnected:" + msg);
    }

    @Override
    public void onRtmpVideoStreaming() {

    }

    @Override
    public void onRtmpAudioStreaming() {

    }

    @Override
    public void onRtmpStopped() {
        if (DEBUG) Log.e(TAG, "onRtmpStopped");
        handleException(null);
    }

    @Override
    public void onRtmpDisconnected() {
        if (DEBUG) Log.e(TAG, "onRtmpDisconnected");
        handleException(null);
    }

    @Override
    public void onRtmpVideoFpsChanged(double fps) {

    }

    @Override
    public void onRtmpVideoBitrateChanged(double bitrate) {

    }

    @Override
    public void onRtmpAudioBitrateChanged(double bitrate) {

    }

    @Override
    public void onRtmpSocketException(SocketException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalStateException(IllegalStateException e) {
        handleException(e);
    }

    @Override
    public void onRecordPause() {
        if (DEBUG) Log.e(TAG, "Record paused");
    }

    @Override
    public void onRecordResume() {
        if (DEBUG) Log.e(TAG, "Record resumed");
    }

    @Override
    public void onRecordStarted(String msg) {
        if (DEBUG) Log.e(TAG, "Recording file: " + msg);
    }

    @Override
    public void onRecordFinished(String msg) {
        if (DEBUG) Log.e(TAG, "MP4 file saved: " + msg);
    }

    @Override
    public void onRecordIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRecordIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

}


