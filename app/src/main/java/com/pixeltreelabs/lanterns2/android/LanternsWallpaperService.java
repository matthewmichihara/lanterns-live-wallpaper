package com.pixeltreelabs.lanterns2.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

public class LanternsWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    public enum SwipeDirection {
        None, Left, Right
    };

    class WallpaperEngine extends Engine {
        private static final String FRAME_PREFIX = "lanterns_";
        private static final int NUM_FRAMES = 15;
        private static final int REFRESH_RATE = 100;
        private static final int WAIT_TIME = 16;
        private final Handler mHandler = new Handler();
        private final DecelerateInterpolator mDecelerateInterpolator = new DecelerateInterpolator(
                1f);
        private final List<Bitmap> mBitmaps = new ArrayList<Bitmap>();
        private final Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };
        public SwipeDirection mSwipeDirection = SwipeDirection.None;
        float initialX = 0;
        float deltaX = 0;
        private int count = 0;
        private int mWidth;
        private int mHeight;

        @Override
        public void onVisibilityChanged(final boolean visible) {
            if (visible) {
                draw();
            } else {
                mHandler.removeCallbacks(mDrawRunnable);
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {

            synchronized (event) {
                try {
                    event.wait(WAIT_TIME);

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        // reset mDeltaX and deltaY
                        deltaX = 0;

                        // get initial positions
                        initialX = event.getRawX();
                    }

                    // when screen is released
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        deltaX = event.getRawX() - initialX;

                        // swipped right
                        if (deltaX > 0) {
                            Log.e("ASDF", "Swipe right " + deltaX);
                            mSwipeDirection = SwipeDirection.Right;
                        }
                        // swiped left
                        if (deltaX < 0) {
                            Log.e("ASDF", "Swipe left " + deltaX);
                            mSwipeDirection = SwipeDirection.Left;
                        }
                    }
                } catch (InterruptedException e) {
                }
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;

            // Clear the existing bitmap list.
            for (Bitmap bitmap : mBitmaps) {
                bitmap.recycle();
            }
            mBitmaps.clear();
            System.gc();

            for (int i = 0; i <= NUM_FRAMES; i++) {
                String drawableName = FRAME_PREFIX
                        + String.format("%05d", i % NUM_FRAMES);

                int resId = getResources().getIdentifier(drawableName,
                        "drawable", getPackageName());

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPurgeable = true;
                options.inInputShareable = true;
                options.inSampleSize = 1;
                Bitmap bm = BitmapFactory.decodeResource(getResources(), resId,
                        options);

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bm, mWidth,
                        mHeight, true);
                mBitmaps.add(scaledBitmap);
                bm.recycle();
                bm = null;
            }

            System.gc();
        }

        public void draw() {
            final SurfaceHolder holder = getSurfaceHolder();

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    draw(c);
                }
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }

            mHandler.removeCallbacks(mDrawRunnable);
            if (count == 0) {
                mHandler.postDelayed(mDrawRunnable, REFRESH_RATE);
            } else {
                mHandler.postDelayed(
                        mDrawRunnable,
                        (long) (REFRESH_RATE * mDecelerateInterpolator
                                .getInterpolation(count / ((float) NUM_FRAMES))));
            }
        }

        private void draw(final Canvas c) {
            if (mSwipeDirection == SwipeDirection.None) {
                c.drawBitmap(mBitmaps.get(0), null, new Rect(0, 0, mWidth,
                        mHeight), null);
            } else if (mSwipeDirection == SwipeDirection.Right) {
                c.drawBitmap(mBitmaps.get(count), null, new Rect(0, 0, mWidth,
                        mHeight), null);
                count++;
            } else if (mSwipeDirection == SwipeDirection.Left) {
                c.drawBitmap(mBitmaps.get(NUM_FRAMES - count), null, new Rect(
                        0, 0, mWidth, mHeight), null);
                count++;
            }

            if (count > NUM_FRAMES) {
                count = 0;
                mSwipeDirection = SwipeDirection.None;
            }
        }
    }
}
