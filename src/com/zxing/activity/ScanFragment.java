package com.zxing.activity;

import java.io.IOException;
import java.lang.reflect.Field;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.zxing.R;
import com.google.zxing.Result;
import com.zxing.camera.CameraManager;
import com.zxing.decode.DecodeThread;
import com.zxing.utils.BeepManager;
import com.zxing.utils.InactivityTimer;
import com.zxing.utils.ScanHandler;

/**
 * @author WangXiang
 * 2015年5月14日下午3:32:06
 */
public class ScanFragment extends Fragment implements SurfaceHolder.Callback {
	private CameraManager cameraManager;
	private ScanHandler handler;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	private SurfaceView scanPreview;
	private RelativeLayout scanCropView;
	private ImageView scanLine;
	private Rect mCropRect;
	private boolean isHasSurface;
	public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
		return inflater.inflate(R.layout.activity_capture, container, false);
	}
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		scanPreview = (SurfaceView) getView().findViewById(R.id.capture_preview);
		scanCropView = (RelativeLayout) getView().findViewById(R.id.capture_crop_view);
		scanLine = (ImageView) getView().findViewById(R.id.capture_scan_line);
		inactivityTimer = new InactivityTimer(getActivity());
		beepManager = new BeepManager(getActivity());
		TranslateAnimation animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,0.88f);
		animation.setDuration(4500);
		animation.setRepeatCount(-1);
		animation.setRepeatMode(Animation.RESTART);
		scanLine.startAnimation(animation);
	}
	
	/**
	 * 得到相机拍摄图片像素尺寸
	 * 得到屏幕像素尺寸
	 * 得到扫描框尺寸
	 * 计算扫描框在图片中的位置
	 * (这里我忽略了标题栏高度)
	 */
	private void initCrop() {
		int cameraWidth = cameraManager.getCameraResolution().y;
		int cameraHeight = cameraManager.getCameraResolution().x;
		/** 获取布局中扫描框的位置信息 */
		int[] location = new int[2];
		scanCropView.getLocationOnScreen(location);
		int cropLeft = location[0];
		int cropTop = location[1] - getStatusBarHeight();
		int cropWidth = scanCropView.getWidth();
		int cropHeight = scanCropView.getHeight();
		/** 获取布局容器的宽高 */
		Display display = getActivity().getWindowManager().getDefaultDisplay();
		int containerWidth = display.getWidth();
		int containerHeight = display.getHeight();
		/** 计算最终截取的矩形的左上角顶点x坐标 */
		int x = cropLeft * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的左上角顶点y坐标 */
		int y = cropTop * cameraHeight / containerHeight;
		/** 计算最终截取的矩形的宽度 */
		int width = cropWidth * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的高度 */
		int height = cropHeight * cameraHeight / containerHeight;
		/** 生成最终的截取的矩形 */
		mCropRect = new Rect(x, y, width + x, height + y);
	}
	
	private int getStatusBarHeight() {
		try {
			Class<?> c = Class.forName("com.android.internal.R$dimen");
			Object obj = c.newInstance();
			Field field = c.getField("status_bar_height");
			int x = Integer.parseInt(field.get(obj).toString());
			return getResources().getDimensionPixelSize(x);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
	public void onResume() {
		super.onResume();
		cameraManager = new CameraManager(getActivity().getApplication());
		handler = null;
		if (isHasSurface) {
			initCamera(scanPreview.getHolder());
		} else {
			scanPreview.getHolder().addCallback(this);
		}
		inactivityTimer.onResume();
	}
	public void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		beepManager.close();
		cameraManager.closeDriver();
		if (!isHasSurface) {
			scanPreview.getHolder().removeCallback(this);
		}
		super.onPause();
	}
	public void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}
	//SurfaceHolder.Callback
	public void surfaceCreated(SurfaceHolder holder) {
		if (!isHasSurface) {
			isHasSurface = true;
			initCamera(holder);
		}
	}
	public void surfaceDestroyed(SurfaceHolder holder) {
		isHasSurface = false;
	}
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
	//SurfaceHolder.Callback  end
	
	/**初始化相机
	 * @param surfaceHolder
	 */
	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);
			if (handler == null) {
				handler = new ScanHandler(this, cameraManager, DecodeThread.ALL_MODE);
			}
			initCrop();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}
	/**扫描后结果传递
	 * @param rawResult  扫描的结果
	 * @param bundle 
	 */
	public void handleDecode(Result rawResult, Bundle bundle) {
		inactivityTimer.onActivity();
		beepManager.playBeepSoundAndVibrate();
		bundle.putInt("width", mCropRect.width());
		bundle.putInt("height", mCropRect.height());
		bundle.putString("result", rawResult.getText());
		startActivity(new Intent(getActivity(), ResultActivity.class).putExtras(bundle));
	}
	public CameraManager getCameraManager() {
		return cameraManager;
	}
	public Handler getHandler() {
		return handler;
	}
	public Rect getCropRect() {
		return mCropRect;
	}
}
