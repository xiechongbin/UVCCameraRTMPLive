package com.bajie.uvccamera.rtmplive.activity;

import android.view.View;
import android.widget.Button;

import com.bajie.uvccamera.rtmplive.R;
import com.bajie.uvccamera.rtmplive.base.BaseActivity;
import com.github.faucamp.simplertmp.RtmpHandler;

import net.ossrs.yasea.SrsCameraView;
import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsPublisher;
import net.ossrs.yasea.SrsRecordHandler;

import java.io.IOException;
import java.net.SocketException;

/**
 * Desc:机身摄像头直播
 * <p>
 * Created by YoungWu on 2019/7/8.
 */
public class InternalCameraLiveActivity extends BaseActivity implements View.OnClickListener {
    private SrsCameraView srsCameraView;
    private Button btn_start;

    private SrsPublisher publisher;

    @Override
    public int getLayout() {
        return R.layout.activity_internal_camera_live;
    }

    @Override
    public void initView(Object obj) {
        srsCameraView = findViewById(R.id.srsCameraView);
        btn_start = findViewById(R.id.btn_start);
    }

    @Override
    public void initData() {
        super.initData();
        initSrsPublisher();
    }

    /**
     * 初始化推流器
     */
    private void initSrsPublisher() {

    }

    @Override
    public void setListener() {
        super.setListener();
        btn_start.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

    }

    private SrsEncodeHandler.SrsEncodeListener srsEncodeListener = new SrsEncodeHandler.SrsEncodeListener() {
        @Override
        public void onNetworkWeak() {

        }

        @Override
        public void onNetworkResume() {

        }

        @Override
        public void onEncodeIllegalArgumentException(IllegalArgumentException e) {

        }
    };

    private RtmpHandler.RtmpListener rtmpListener = new RtmpHandler.RtmpListener() {
        @Override
        public void onRtmpConnecting(String msg) {

        }

        @Override
        public void onRtmpConnected(String msg) {

        }

        @Override
        public void onRtmpVideoStreaming() {

        }

        @Override
        public void onRtmpAudioStreaming() {

        }

        @Override
        public void onRtmpStopped() {

        }

        @Override
        public void onRtmpDisconnected() {

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

        }

        @Override
        public void onRtmpIOException(IOException e) {

        }

        @Override
        public void onRtmpIllegalArgumentException(IllegalArgumentException e) {

        }

        @Override
        public void onRtmpIllegalStateException(IllegalStateException e) {

        }
    };

    private SrsRecordHandler.SrsRecordListener srsRecordListener = new SrsRecordHandler.SrsRecordListener() {
        @Override
        public void onRecordPause() {

        }

        @Override
        public void onRecordResume() {

        }

        @Override
        public void onRecordStarted(String msg) {

        }

        @Override
        public void onRecordFinished(String msg) {

        }

        @Override
        public void onRecordIllegalArgumentException(IllegalArgumentException e) {

        }

        @Override
        public void onRecordIOException(IOException e) {

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
