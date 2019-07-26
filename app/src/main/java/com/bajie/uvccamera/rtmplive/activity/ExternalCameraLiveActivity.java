package com.bajie.uvccamera.rtmplive.activity;

import android.hardware.usb.UsbDevice;
import android.view.WindowManager;

import com.bajie.uvccamera.rtmplive.R;
import com.bajie.uvccamera.rtmplive.base.BaseActivity;
import com.orhanobut.logger.Logger;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.UVCCameraTextureView;

/**
 * Desc:外置摄像头直播
 * <p>
 * Created by YoungWu on 2019/7/8.
 */
public class ExternalCameraLiveActivity extends BaseActivity {
    private UVCCameraTextureView uvcCameraView;

    private USBMonitor usbMonitor;
    private UVCCameraHandler uvcCameraHandler;

    @Override
    public int getLayout() {
        return R.layout.activity_external_camera_live;
    }

    @Override
    public void initView(Object obj) {
        uvcCameraView = findViewById(R.id.uvcCameraView);
    }

    @Override
    public void initData() {
        super.initData();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        usbMonitor = new USBMonitor(this, onDeviceConnectListener);
        uvcCameraHandler = UVCCameraHandler.createHandler(this, uvcCameraView, UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT);
    }

    private USBMonitor.OnDeviceConnectListener onDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            Logger.e("USBMonitor:onAttach");
            usbMonitor.requestPermission(device);
        }

        @Override
        public void onDetach(UsbDevice device) {
            Logger.e("USBMonitor:onDetach");
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            Logger.e("USBMonitor:onConnect");
            uvcCameraHandler.open(ctrlBlock);
            uvcCameraHandler.startPreview(uvcCameraView.getSurfaceTexture());
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            Logger.e("USBMonitor:onDisconnect");
            uvcCameraHandler.close();
        }

        @Override
        public void onCancel(UsbDevice device) {
            Logger.e("USBMonitor:onCancel");
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        usbMonitor.register();
        uvcCameraView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        usbMonitor.unregister();
        uvcCameraView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        uvcCameraHandler.release();
        usbMonitor.destroy();
    }
}
