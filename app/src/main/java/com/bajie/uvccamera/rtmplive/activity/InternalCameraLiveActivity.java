package com.bajie.uvccamera.rtmplive.activity;

import android.content.res.Configuration;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.bajie.uvccamera.rtmplive.R;
import com.bajie.uvccamera.rtmplive.base.BaseActivity;
import com.bajie.uvccamera.rtmplive.util.ToastUtils;
import com.github.faucamp.simplertmp.RtmpHandler;
import com.orhanobut.logger.Logger;
import com.seu.magicfilter.utils.MagicFilterType;

import net.ossrs.yasea.SrsCameraView;
import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsLiveConfig;
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
    /**
     * 测试推流地址
     */
    public static final String TEST_URL = "rtmp://119.29.184.33:1935/live/livestream_863134036763822";
    private SrsCameraView srsCameraView;
    private Button btn_start;
    private Button btn_back_camera;

    private SrsPublisher publisher;
    private boolean isLive = false;
    private boolean isBack = false;

    @Override
    public int getLayout() {
        return R.layout.activity_internal_camera_live;
    }

    @Override
    public void initView(Object obj) {
        srsCameraView = findViewById(R.id.srsCameraView);
        btn_start = findViewById(R.id.btn_start);
        btn_back_camera = findViewById(R.id.btn_back_camera);
    }

    @Override
    public void initData() {
        super.initData();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initSrsPublisher();
    }

    /**
     * 初始化推流器
     */
    private void initSrsPublisher() {
        publisher = new SrsPublisher(srsCameraView);
        publisher.setEncodeHandler(new SrsEncodeHandler(srsEncodeListener));
        publisher.setRecordHandler(new SrsRecordHandler(srsRecordListener));
        publisher.setRtmpHandler(new RtmpHandler(rtmpListener));
        publisher.setPreviewResolution(SrsLiveConfig.HIGH_DEFINITION_WIDTH, SrsLiveConfig.HIGH_DEFINITION_HEIGHT);
        publisher.setOutputResolution(SrsLiveConfig.HIGH_DEFINITION_WIDTH, SrsLiveConfig.HIGH_DEFINITION_HEIGHT);
        publisher.setScreenOrientation(Configuration.ORIENTATION_PORTRAIT);
        publisher.switchCameraFilter(MagicFilterType.NONE);
        publisher.setVideoHDMode();
        publisher.switchToHardEncoder();
    }

    @Override
    public void setListener() {
        super.setListener();
        btn_start.setOnClickListener(this);
        btn_back_camera.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                //开始或结束直播
                if (isLive) {
                    btn_start.setText("开始");
                    isLive = false;
                    stopLive();
                } else {
                    btn_start.setText("结束");
                    isLive = true;
                    startLive();
                }
                break;
            case R.id.btn_back_camera:
                //切换前置或后置摄像头
                if (isBack) {
                    btn_back_camera.setText("后置");
                    isBack = false;
                    switchFrontCamera();
                } else {
                    btn_back_camera.setText("前置");
                    isBack = true;
                    switchBackCamera();
                }
                break;
        }
    }

    /**
     * 开始直播
     */
    private void startLive() {
        publisher.startPublish(TEST_URL);
    }

    /**
     * 结束直播
     */
    private void stopLive() {
        publisher.stopPublish();
    }

    /**
     * 切换前置摄像头
     */
    private void switchFrontCamera() {
        publisher.switchCameraFace(1);
    }

    /**
     * 切换后置摄像头
     */
    private void switchBackCamera() {
        publisher.switchCameraFace(0);
    }

    private SrsEncodeHandler.SrsEncodeListener srsEncodeListener = new SrsEncodeHandler.SrsEncodeListener() {
        @Override
        public void onNetworkWeak() {
            Logger.e("onNetworkWeak");
            ToastUtils.toast(InternalCameraLiveActivity.this, "onNetworkWeak");
        }

        @Override
        public void onNetworkResume() {
            Logger.e("onNetworkResume");
            ToastUtils.toast(InternalCameraLiveActivity.this, "onNetworkResume");
        }

        @Override
        public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
            Logger.e(e, "onEncodeIllegalArgumentException");
            ToastUtils.toast(InternalCameraLiveActivity.this, "onEncodeIllegalArgumentException");
        }
    };

    private RtmpHandler.RtmpListener rtmpListener = new RtmpHandler.RtmpListener() {
        @Override
        public void onRtmpConnecting(String msg) {
            Logger.e("onRtmpConnecting:" + msg);
        }

        @Override
        public void onRtmpConnected(String msg) {
            Logger.e("onRtmpConnected:" + msg);
        }

        @Override
        public void onRtmpVideoStreaming() {
            Logger.e("onRtmpVideoStreaming");
        }

        @Override
        public void onRtmpAudioStreaming() {
            Logger.e("onRtmpAudioStreaming");
        }

        @Override
        public void onRtmpStopped() {
            Logger.e("onRtmpStopped");
        }

        @Override
        public void onRtmpDisconnected() {
            Logger.e("onRtmpDisconnected");
        }

        @Override
        public void onRtmpVideoFpsChanged(double fps) {
            Logger.e("onRtmpVideoFpsChanged:" + fps);
        }

        @Override
        public void onRtmpVideoBitrateChanged(double bitrate) {
            Logger.e("onRtmpVideoBitrateChanged:" + bitrate);
        }

        @Override
        public void onRtmpAudioBitrateChanged(double bitrate) {
            Logger.e("onRtmpAudioBitrateChanged:" + bitrate);
        }

        @Override
        public void onRtmpSocketException(SocketException e) {
            Logger.e(e, "onRtmpSocketException");
        }

        @Override
        public void onRtmpIOException(IOException e) {
            Logger.e(e, "onRtmpIOException");
        }

        @Override
        public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
            Logger.e(e, "onRtmpIllegalArgumentException");
        }

        @Override
        public void onRtmpIllegalStateException(IllegalStateException e) {
            Logger.e(e, "onRtmpIllegalStateException");
        }
    };

    private SrsRecordHandler.SrsRecordListener srsRecordListener = new SrsRecordHandler.SrsRecordListener() {
        @Override
        public void onRecordPause() {
            Logger.e("onRecordPause");
        }

        @Override
        public void onRecordResume() {
            Logger.e("onRecordResume");
        }

        @Override
        public void onRecordStarted(String msg) {
            Logger.e("onRecordStarted:" + msg);
        }

        @Override
        public void onRecordFinished(String msg) {
            Logger.e("onRecordFinished:" + msg);
        }

        @Override
        public void onRecordIllegalArgumentException(IllegalArgumentException e) {
            Logger.e(e, "onRecordIllegalArgumentException");
        }

        @Override
        public void onRecordIOException(IOException e) {
            Logger.e(e, "onRecordIOException");
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopLive();
    }
}
