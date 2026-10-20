package com.pinpic.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class InoLoadingIndicator extends View {
	
	private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private Path path = new Path();
	
	private int color = 0xFF0D47A1;
	
	private float morph = 0f;
	private float baseRotation = 0f;
	private float rotationSpeedMultiplier = 0.70f;
	private float lastRotationValue = 0f;
	
	private int currentShapeIndex = 0;
	private int nextShapeIndex = 1;
	
	private ValueAnimator morphAnimator;
	private ValueAnimator rotationAnimator;
	private Handler handler = new Handler(Looper.getMainLooper());
	
	private static final int POINTS = 64;
	private static final int SHAPE_COUNT = 7;
	
	private float[][] shapes;
	
	// Timing
	private static final int HOLD_TIME = 750;
	private static final int MORPH_TIME = 700;
	private static final int ROTATION_DURATION = 2500;
	
	private boolean isMorphing = false;
	
	public InoLoadingIndicator(Context c) {
		super(c);
		init();
	}
	
	public InoLoadingIndicator(Context c, AttributeSet a) {
		super(c, a);
		init();
	}
	
	public InoLoadingIndicator(Context c, AttributeSet a, int s) {
		super(c, a, s);
		init();
	}
	
	private void init() {
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(color);
		
		generateShapes();
		startRotation();
		startSequence();
	}
	
	private void generateShapes() {
		
		shapes = new float[SHAPE_COUNT][POINTS];
		
		for (int i = 0; i < POINTS; i++) {
			
			float a = (float) (i * Math.PI * 2 / POINTS);
			
			// Bintang
			shapes[0][i] = 1f + 0.11f * (float) Math.sin(a * 9);
			
			// Bunga
			shapes[1][i] = 1f + 0.058f * (float) Math.sin(a * 10);
			
			// Pentagon
			shapes[2][i] = 1f + 0.048f * (float) Math.sin(a * 5);
			
			// Pill
			float stretch = 0.13f * (float)Math.cos(a * 2);
			shapes[3][i] = 1f - stretch;
			
			// Sunny
			shapes[4][i] = 1f + 0.097f * (float) Math.sin(a * 8);
			
			// 4side Cookie
			float s = (float)Math.sin(a * 4);
			shapes[5][i] = 1f + 0.12f * s - 0.04f * s * s;
			
			// Blob memanjang
			float base = 1f + 0.090f * (float) Math.cos(a * 4);
			float verticalDrop = 0.16f * (float) Math.cos(a * 2);
			float pinch = 0.13f * (float) Math.cos(a * 2) * (float) Math.cos(a * 2);
			shapes[6][i] = base - verticalDrop - pinch;
		}
	}
	
	private void startRotation() {
		
		rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
		
		rotationAnimator.setDuration(ROTATION_DURATION);
		rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
		rotationAnimator.setInterpolator(new LinearInterpolator());
		
		rotationAnimator.addUpdateListener(animation -> {
			
			float value = (float) animation.getAnimatedValue();
			
			float delta = value - lastRotationValue;
			
			if (delta < 0) delta += 360f;
			
			baseRotation += delta * rotationSpeedMultiplier;
			
			lastRotationValue = value;
			
			invalidate();
		});
		
		rotationAnimator.start();
	}
	
	private void startSequence() {
		
		currentShapeIndex = 0;
		nextShapeIndex = 1;
		
		morph = 0f;
		isMorphing = false;
		
		invalidate();
		
		nextShape();
	}
	
	private void nextShape() {
		
		handler.postDelayed(() -> {
			
			if (!isMorphing) {
				startMorph();
			}
			
		}, HOLD_TIME);
	}
	
	private void startMorph() {
		
		isMorphing = true;
		
		nextShapeIndex = (currentShapeIndex + 1) % SHAPE_COUNT;
		
		// percepat rotasi
		animateRotationSpeed(rotationSpeedMultiplier, 1.95f, 23);
		
		morphAnimator = ValueAnimator.ofFloat(0f, 1f);
		
		morphAnimator.setDuration(MORPH_TIME);
		morphAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
		
		morphAnimator.addUpdateListener(animation -> {
			
			morph = (float) animation.getAnimatedValue();
			
			invalidate();
		});
		
		morphAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
			
			@Override
			public void onAnimationEnd(android.animation.Animator animation) {
				
				currentShapeIndex = nextShapeIndex;
				
				morph = 0f;
				isMorphing = false;
				
				invalidate();
				
				// kembalikan rotasi normal
				animateRotationSpeed(rotationSpeedMultiplier, 0.70f, 45);
				
				nextShape();
			}
		});
		
		morphAnimator.start();
	}
	
	private void animateRotationSpeed(float from, float to, long duration) {
		
		ValueAnimator speedAnim = ValueAnimator.ofFloat(from, to);
		
		speedAnim.setDuration(duration);
		speedAnim.setInterpolator(new AccelerateDecelerateInterpolator());
		
		speedAnim.addUpdateListener(a -> {
			
			rotationSpeedMultiplier = (float) a.getAnimatedValue();
			
		});
		
		speedAnim.start();
	}
	
	public void setColor(int c) {
		
		color = c;
		paint.setColor(c);
		
		invalidate();
	}
	
	@Override
	protected void onMeasure(int w, int h) {
		
		int widthMode = MeasureSpec.getMode(w);
		int heightMode = MeasureSpec.getMode(h);
		
		int widthSize = MeasureSpec.getSize(w);
		int heightSize = MeasureSpec.getSize(h);
		
		int defaultSize = dp(55);
		
		int width;
		int height;
		
		if (widthMode == MeasureSpec.EXACTLY) {
			width = widthSize;
		} else {
			width = defaultSize;
		}
		
		if (heightMode == MeasureSpec.EXACTLY) {
			height = heightSize;
		} else {
			height = defaultSize;
		}
		
		int size = Math.min(width, height);
		
		setMeasuredDimension(size, size);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		
		float w = getWidth();
		float h = getHeight();
		
		float cx = w / 2f;
		float cy = h / 2f;
		
		float radius = Math.min(w, h) * 0.35f;
		
		float[] currentShape = shapes[currentShapeIndex];
		float[] nextShape = shapes[nextShapeIndex];
		
		canvas.save();
		canvas.translate(cx, cy);
		
		canvas.rotate(baseRotation);
		
		path.reset();
		
		for (int i = 0; i < POINTS; i++) {
			
			float r;
			
			if (isMorphing) {
				
				r = currentShape[i] + (nextShape[i] - currentShape[i]) * morph;
				
			} else {
				
				r = currentShape[i];
			}
			
			float ang = (float) (i * Math.PI * 2 / POINTS);
			
			float x = (float) Math.cos(ang) * radius * r;
			float y = (float) Math.sin(ang) * radius * r;
			
			if (i == 0) {
				path.moveTo(x, y);
			} else {
				path.lineTo(x, y);
			}
		}
		
		path.close();
		
		canvas.drawPath(path, paint);
		
		canvas.restore();
	}
	
	@Override
	protected void onDetachedFromWindow() {
		
		super.onDetachedFromWindow();
		
		handler.removeCallbacksAndMessages(null);
		
		if (morphAnimator != null) morphAnimator.cancel();
		if (rotationAnimator != null) rotationAnimator.cancel();
	}
	
	private int dp(int v) {
		
		return (int) (v * getResources().getDisplayMetrics().density);
	}
}
