package com.pinpic.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropImageView extends View {
	
	private Bitmap sourceBitmap;
	private Bitmap croppedBitmap;
	
	// Crop box properties
	private final RectF cropRect = new RectF();
	private final RectF imageBounds = new RectF();
	
	// Touch handling
	private static final int TOUCH_NONE = 0;
	private static final int TOUCH_DRAG = 1;
	private static final int TOUCH_RESIZE_TOP_LEFT = 2;
	private static final int TOUCH_RESIZE_TOP_RIGHT = 3;
	private static final int TOUCH_RESIZE_BOTTOM_LEFT = 4;
	private static final int TOUCH_RESIZE_BOTTOM_RIGHT = 5;
	private int touchMode = TOUCH_NONE;
	
	private float lastTouchX, lastTouchY;
	
	// Paint objects
	private Paint darkPaint;
	private Paint borderPaint;
	private Paint cornerPaint;
	private Paint gridPaint;
	
	// Dimensions & Scale
	private int viewWidth, viewHeight;
	private float imageScale = 1.0f;
	private float density;
	
	// Configurable sizes
	private static final float MIN_CROP_SIZE_DP = 60;
	private static final float HANDLE_TOUCH_SIZE_DP = 36; 
	private static final float CORNER_LINE_LENGTH_DP = 20;
	
	private float minCropSize;
	private float handleTouchSize;
	private float cornerLineLength;
	
	// Aspect Ratio Control
	private boolean isAspectRatioLocked = false;
	private float aspectRatio = 1.0f; // width / height
	
	// Output Mode
	private boolean isOutputSquare = false;
	private static final int OUTPUT_SIZE = 512;
	
	private CropListener cropListener;
	
	public interface CropListener {
		void onCropReady(Bitmap croppedBitmap);
		void onCropCancelled();
	}
	
	public CropImageView(Context context) {
		this(context, null);
	}
	
	public CropImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public CropImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}
	
	private void init() {
		density = getResources().getDisplayMetrics().density;
		minCropSize = MIN_CROP_SIZE_DP * density;
		handleTouchSize = HANDLE_TOUCH_SIZE_DP * density;
		cornerLineLength = CORNER_LINE_LENGTH_DP * density;
		
		// Dark overlay (semi-transparent black outside crop area)
		darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		darkPaint.setColor(0x99000000);
		
		// Border paint
		borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		borderPaint.setColor(Color.WHITE);
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setStrokeWidth(1.5f * density);
		
		// Corner handle paint
		cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		cornerPaint.setColor(Color.WHITE);
		cornerPaint.setStyle(Paint.Style.STROKE);
		cornerPaint.setStrokeWidth(3.5f * density);
		cornerPaint.setStrokeCap(Paint.Cap.ROUND);
		
		// Grid paint (rule of thirds)
		gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		gridPaint.setColor(0x66FFFFFF);
		gridPaint.setStyle(Paint.Style.STROKE);
		gridPaint.setStrokeWidth(1 * density);
	}
	
	public void setImage(Bitmap bitmap) {
		if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
			sourceBitmap.recycle();
		}
		if (croppedBitmap != null && !croppedBitmap.isRecycled()) {
			croppedBitmap.recycle();
			croppedBitmap = null;
		}
		
		this.sourceBitmap = bitmap;
		if (viewWidth > 0 && viewHeight > 0) {
			calculateImageBounds();
			initializeCropBox();
		}
		invalidate();
	}
	
	private void calculateImageBounds() {
		if (sourceBitmap == null || viewWidth == 0 || viewHeight == 0) return;
		
		float bitmapWidth = sourceBitmap.getWidth();
		float bitmapHeight = sourceBitmap.getHeight();
		
		float viewRatio = (float) viewWidth / viewHeight;
		float bitmapRatio = bitmapWidth / bitmapHeight;
		
		float imgW, imgH, imgL, imgT;
		
		if (bitmapRatio > viewRatio) {
			imgW = viewWidth;
			imgH = viewWidth / bitmapRatio;
			imgL = 0;
			imgT = (viewHeight - imgH) / 2f;
		} else {
			imgH = viewHeight;
			imgW = viewHeight * bitmapRatio;
			imgL = (viewWidth - imgW) / 2f;
			imgT = 0;
		}
		
		imageBounds.set(imgL, imgT, imgL + imgW, imgT + imgH);
		imageScale = imgW / bitmapWidth;
	}
	
	private void initializeCropBox() {
		if (imageBounds.isEmpty()) return;
		
		float width = imageBounds.width() * 0.8f;
		float height = imageBounds.height() * 0.8f;
		
		if (isAspectRatioLocked) {
			if (width / height > aspectRatio) {
				width = height * aspectRatio;
			} else {
				height = width / aspectRatio;
			}
		}
		
		float cx = imageBounds.centerX();
		float cy = imageBounds.centerY();
		
		cropRect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
		clampCropBox();
	}
	
	private void clampCropBox() {
		// Enforce Minimum Dimensions
		if (cropRect.width() < minCropSize) {
			float cx = cropRect.centerX();
			cropRect.left = cx - minCropSize / 2f;
			cropRect.right = cx + minCropSize / 2f;
		}
		if (cropRect.height() < minCropSize) {
			float cy = cropRect.centerY();
			cropRect.top = cy - minCropSize / 2f;
			cropRect.bottom = cy + minCropSize / 2f;
		}
		
		// Clamp inside image bounds
		if (cropRect.left < imageBounds.left) cropRect.offset(imageBounds.left - cropRect.left, 0);
		if (cropRect.top < imageBounds.top) cropRect.offset(0, imageBounds.top - cropRect.top);
		if (cropRect.right > imageBounds.right) cropRect.offset(imageBounds.right - cropRect.right, 0);
		if (cropRect.bottom > imageBounds.bottom) cropRect.offset(0, imageBounds.bottom - cropRect.bottom);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		viewWidth = w;
		viewHeight = h;
		if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
			calculateImageBounds();
			initializeCropBox();
		}
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		if (sourceBitmap == null || sourceBitmap.isRecycled()) return;
		
		// 1. Draw source image scaled to bounds
		canvas.drawBitmap(sourceBitmap, null, imageBounds, null);
		
		// 2. Draw dim translucent overlay
		Path path = new Path();
		path.addRect(0, 0, viewWidth, viewHeight, Path.Direction.CW);
		path.addRect(cropRect, Path.Direction.CCW);
		canvas.drawPath(path, darkPaint);
		
		// 3. Draw crop box border
		canvas.drawRect(cropRect, borderPaint);
		
		// 4. Draw rule-of-thirds grid
		float x1 = cropRect.left + cropRect.width() / 3f;
		float x2 = cropRect.left + (cropRect.width() * 2f) / 3f;
		float y1 = cropRect.top + cropRect.height() / 3f;
		float y2 = cropRect.top + (cropRect.height() * 2f) / 3f;
		
		canvas.drawLine(x1, cropRect.top, x1, cropRect.bottom, gridPaint);
		canvas.drawLine(x2, cropRect.top, x2, cropRect.bottom, gridPaint);
		canvas.drawLine(cropRect.left, y1, cropRect.right, y1, gridPaint);
		canvas.drawLine(cropRect.left, y2, cropRect.right, y2, gridPaint);
		
		// 5. Draw bold L-shaped corners
		drawCorner(canvas, cropRect.left, cropRect.top, 1, 1);
		drawCorner(canvas, cropRect.right, cropRect.top, -1, 1);
		drawCorner(canvas, cropRect.left, cropRect.bottom, 1, -1);
		drawCorner(canvas, cropRect.right, cropRect.bottom, -1, -1);
	}
	
	private void drawCorner(Canvas canvas, float x, float y, float dx, float dy) {
		canvas.drawLine(x, y, x + (dx * cornerLineLength), y, cornerPaint);
		canvas.drawLine(x, y, x, y + (dy * cornerLineLength), cornerPaint);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (sourceBitmap == null || sourceBitmap.isRecycled()) return false;
		
		float x = event.getX();
		float y = event.getY();
		
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			touchMode = getTouchMode(x, y);
			lastTouchX = x;
			lastTouchY = y;
			return touchMode != TOUCH_NONE;
			
			case MotionEvent.ACTION_MOVE:
			if (touchMode != TOUCH_NONE) {
				float dx = x - lastTouchX;
				float dy = y - lastTouchY;
				handleMove(dx, dy);
				lastTouchX = x;
				lastTouchY = y;
				invalidate();
				return true;
			}
			break;
			
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
			touchMode = TOUCH_NONE;
			break;
		}
		return super.onTouchEvent(event);
	}
	
	private int getTouchMode(float x, float y) {
		// Pilih sudut TERDEKAT dalam jangkauan sentuh (bukan sekadar sudut pertama yang cocok),
		// supaya saat kotak crop kecil sudut yang disentuh tidak tertukar.
		int best = TOUCH_NONE;
		double bestDist = handleTouchSize;

		double d = Math.hypot(x - cropRect.left, y - cropRect.top);
		if (d <= bestDist) { bestDist = d; best = TOUCH_RESIZE_TOP_LEFT; }
		d = Math.hypot(x - cropRect.right, y - cropRect.top);
		if (d <= bestDist) { bestDist = d; best = TOUCH_RESIZE_TOP_RIGHT; }
		d = Math.hypot(x - cropRect.left, y - cropRect.bottom);
		if (d <= bestDist) { bestDist = d; best = TOUCH_RESIZE_BOTTOM_LEFT; }
		d = Math.hypot(x - cropRect.right, y - cropRect.bottom);
		if (d <= bestDist) { bestDist = d; best = TOUCH_RESIZE_BOTTOM_RIGHT; }

		if (best != TOUCH_NONE) return best;
		if (cropRect.contains(x, y)) return TOUCH_DRAG;
		return TOUCH_NONE;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	/**
	 * Resize dari salah satu sudut. Sudut BERLAWANAN dijadikan titik tetap (anchor), lalu ukuran baru
	 * dihitung dari arah gerakan jari. Cara ini berlaku sama untuk keempat sudut, sehingga tidak ada
	 * lagi tanda (+/-) yang terbalik seperti sebelumnya (hanya kanan-bawah yang benar).
	 */
	private void handleMove(float dx, float dy) {
		if (touchMode == TOUCH_DRAG) {
			// Drag seluruh kotak crop
			cropRect.offset(dx, dy);
			clampCropBox();
			return;
		}

		final boolean movesLeft = touchMode == TOUCH_RESIZE_TOP_LEFT || touchMode == TOUCH_RESIZE_BOTTOM_LEFT;
		final boolean movesTop = touchMode == TOUCH_RESIZE_TOP_LEFT || touchMode == TOUCH_RESIZE_TOP_RIGHT;

		// Arah tumbuh: +1 jika sudut ada di kanan/bawah, -1 jika di kiri/atas
		final float sx = movesLeft ? -1f : 1f;
		final float sy = movesTop ? -1f : 1f;

		// Titik tetap = sudut yang berlawanan dengan sudut yang digeser
		final float anchorX = movesLeft ? cropRect.right : cropRect.left;
		final float anchorY = movesTop ? cropRect.bottom : cropRect.top;

		// Posisi baru sudut yang digeser = posisi lama + gerakan jari
		float cornerX = (movesLeft ? cropRect.left : cropRect.right) + dx;
		float cornerY = (movesTop ? cropRect.top : cropRect.bottom) + dy;

		// Lebar/tinggi baru diukur dari anchor ke arah tumbuh
		float w = sx * (cornerX - anchorX);
		float h = sy * (cornerY - anchorY);

		// Ruang maksimum sampai tepi gambar
		final float maxW = (sx > 0) ? imageBounds.right - anchorX : anchorX - imageBounds.left;
		final float maxH = (sy > 0) ? imageBounds.bottom - anchorY : anchorY - imageBounds.top;

		if (isAspectRatioLocked) {
			// Ikuti gerakan terbesar (dalam satuan lebar), lalu tinggi mengikuti rasio
			w = Math.max(w, h * aspectRatio);
			final float minW = Math.max(minCropSize, minCropSize * aspectRatio);
			final float limitW = Math.min(maxW, maxH * aspectRatio);
			w = Math.min(Math.max(w, minW), limitW);
			h = w / aspectRatio;
		} else {
			w = Math.min(Math.max(w, minCropSize), maxW);
			h = Math.min(Math.max(h, minCropSize), maxH);
		}

		float left = (sx > 0) ? anchorX : anchorX - w;
		float right = (sx > 0) ? anchorX + w : anchorX;
		float top = (sy > 0) ? anchorY : anchorY - h;
		float bottom = (sy > 0) ? anchorY + h : anchorY;

		cropRect.set(
			clamp(left, imageBounds.left, imageBounds.right),
			clamp(top, imageBounds.top, imageBounds.bottom),
			clamp(right, imageBounds.left, imageBounds.right),
			clamp(bottom, imageBounds.top, imageBounds.bottom));
	}

	public void doCrop() {
		if (sourceBitmap == null || sourceBitmap.isRecycled()) return;
		
		// Calculate original bitmap crop coordinates
		float left = (cropRect.left - imageBounds.left) / imageScale;
		float top = (cropRect.top - imageBounds.top) / imageScale;
		float width = cropRect.width() / imageScale;
		float height = cropRect.height() / imageScale;
		
		// Sanitize bounds
		int x = Math.max(0, (int) left);
		int y = Math.max(0, (int) top);
		int w = Math.min(sourceBitmap.getWidth() - x, (int) width);
		int h = Math.min(sourceBitmap.getHeight() - y, (int) height);
		
		if (w <= 0 || h <= 0) return;
		
		try {
			Bitmap cropped = Bitmap.createBitmap(sourceBitmap, x, y, w, h);
			
			if (isOutputSquare) {
				Bitmap scaled = Bitmap.createScaledBitmap(cropped, OUTPUT_SIZE, OUTPUT_SIZE, true);
				if (scaled != cropped) cropped.recycle();
				croppedBitmap = scaled;
			} else {
				croppedBitmap = cropped;
			}
			
			if (cropListener != null) {
				cropListener.onCropReady(croppedBitmap);
			}
		} catch (OutOfMemoryError | Exception e) {
			e.printStackTrace();
		}
	}
	
	// ============ Public Configuration APIs ============
	
	public void lockSquareAspect() {
		setAspectRatio(1.0f);
	}
	
	public void setAspectRatio(float ratio) {
		this.aspectRatio = ratio;
		this.isAspectRatioLocked = true;
		initializeCropBox();
		invalidate();
	}
	
	public void unlockAspectRatio() {
		this.isAspectRatioLocked = false;
	}
	
	public void setOutputSquare(boolean square) {
		this.isOutputSquare = square;
	}
	
	public void setCropListener(CropListener listener) {
		this.cropListener = listener;
	}
	
	public Bitmap getCroppedBitmap() {
		return croppedBitmap;
	}
	
	public void resetCrop() {
		initializeCropBox();
		invalidate();
	}
	
	public void clearImage() {
		if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
			sourceBitmap.recycle();
			sourceBitmap = null;
		}
		if (croppedBitmap != null && !croppedBitmap.isRecycled()) {
			croppedBitmap.recycle();
			croppedBitmap = null;
		}
		invalidate();
	}
	
	public boolean isOutputSquare() {
		return isOutputSquare;
	}
	
	public boolean isAspectRatioLocked() {
		return isAspectRatioLocked;
	}
}
