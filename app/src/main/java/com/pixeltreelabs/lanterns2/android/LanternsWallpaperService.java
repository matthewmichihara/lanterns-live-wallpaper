package com.pixeltreelabs.lanterns2.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

public class LanternsWallpaperService extends WallpaperService {
    private static final String TAG = LanternsWallpaperService.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    private enum SwipeDirection {
        None, Left, Right
    }

    class WallpaperEngine extends Engine {
        /** Frame prefix of image stored in drawable directory. Assume images have names such as prefix_00004.png. */
        private static final String FRAME_PREFIX = "lanterns_";
        /** Highest frame mTickCount. Assume frames up to FRAME_MAX_DIGIT exist starting from 0. */
        private static final int FRAME_MAX_DIGIT = 15;
        /** Refresh rate of the wallpaper. */
        private static final int REFRESH_RATE = 100;
        /** Something used for calculating swipe direction. */
        private static final int SWIPE_WAIT_TIME = 16;
        /** This stores our bitmaps. */
        private final List<Bitmap> mBitmaps = new ArrayList<Bitmap>();
        /** Handler to post `draw` runnables to. */
        private final Handler mHandler = new Handler();
        /**
         * Transformation matrix used for drawing bitmap background. Default matrix since we don't want any
         * transformations.
         */
        private final Matrix mTransformationMatrix = new Matrix();
        /** Bitmap creation options. */
        private final BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();
        /** Add some deceleration to our `draw` calls. */
        private final DecelerateInterpolator mDecelerateInterpolator = new DecelerateInterpolator(
                1f);
        /** Runnable that is posted to our handler every {@link #REFRESH_RATE} milliseconds. */
        private final Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };
        /** The direction that the screen has been swiped in. */
        private SwipeDirection mSwipeDirection = SwipeDirection.None;
        /** Variables used to determine current swipe direction. */
        private float mInitialX = 0;
        private float mDeltaX = 0;
        /** Current width of the screen. */
        private int mScreenWidth;
        /** Current height of the screen. */
        private int mScreenHeight;
        /** Incremented everytime `draw` is called. */
        private int mTickCount = 0;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            // Set up our bitmap creation options.
            // Don't need the alpha channel, so use this to reduce memory footprint.
            mBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;

            // These options probably don't do anything, but whatever.
            mBitmapOptions.inPurgeable = true;
            mBitmapOptions.inInputShareable = true;
        }

        @Override
        public void onVisibilityChanged(final boolean visible) {
            if (visible) {
                draw();
            } else {
                mHandler.removeCallbacks(mDrawRunnable);
            }
        }

        /** Re-scale bitmaps if the surface view changes. */
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mScreenWidth = width;
            mScreenHeight = height;

            // Clear the existing bitmap list. No idea if this is a reasonable way to do this.
            for (Bitmap bitmap : mBitmaps) {
                bitmap.recycle();
            }

            mBitmaps.clear();
            System.gc();

            // Populate our list of bitmaps.
            for (int i = 0; i <= FRAME_MAX_DIGIT; i++) {
                String drawableName = FRAME_PREFIX
                        + String.format("%05d", i % FRAME_MAX_DIGIT);

                int resId = getResources().getIdentifier(drawableName,
                        "drawable", getPackageName());

                Bitmap bm = BitmapFactory.decodeResource(getResources(), resId,
                        mBitmapOptions);

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bm, mScreenWidth,
                        mScreenHeight, true);
                mBitmaps.add(scaledBitmap);
                bm.recycle();
            }

            // Probably useless.
            System.gc();
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            // I really don't know how this part works or why it's necessary.
            synchronized (event) {
                try {
                    event.wait(SWIPE_WAIT_TIME);

                    // When screen is pressed.
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        // Reset mDeltaX.
                        mDeltaX = 0;

                        // Get initial position.
                        mInitialX = event.getRawX();
                    }

                    // When screen is released.
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mDeltaX = event.getRawX() - mInitialX;

                        // Swiped right.
                        if (mDeltaX > 0) {
                            Log.d(TAG, "Swipe right " + mDeltaX);
                            mSwipeDirection = SwipeDirection.Right;
                        }
                        // Swiped left.
                        if (mDeltaX < 0) {
                            Log.d(TAG, "Swipe left " + mDeltaX);
                            mSwipeDirection = SwipeDirection.Left;
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error", e);
                }
            }
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
            if (mTickCount == 0) {
                mHandler.postDelayed(mDrawRunnable, REFRESH_RATE);
            } else {
                // If we're in the middle of an animation, use the decelerate interoplator to determine the refresh
                // delay.
                mHandler.postDelayed(
                        mDrawRunnable,
                        (long) (REFRESH_RATE * mDecelerateInterpolator
                                .getInterpolation(mTickCount / ((float) FRAME_MAX_DIGIT))));
            }
        }

        /**
         * Draw a frame to the canvas. The frame that is drawn depends on where we are in the animation and the
         * direction that the screen was swiped.
         */
        private void draw(final Canvas c) {
            switch (mSwipeDirection) {
                case None:
                    c.drawBitmap(mBitmaps.get(0), mTransformationMatrix, null);
                    break;
                case Right:
                    c.drawBitmap(mBitmaps.get(mTickCount), mTransformationMatrix, null);
                    mTickCount++;
                    break;
                case Left:
                    c.drawBitmap(mBitmaps.get(FRAME_MAX_DIGIT - mTickCount), mTransformationMatrix, null);
                    mTickCount++;
                    break;
                default:
                    Log.e(TAG, "Unknown swipe direction");
            }

            // When we hit the highest frame, reset the tick and swipe direction state.
            if (mTickCount > FRAME_MAX_DIGIT) {
                mTickCount = 0;
                mSwipeDirection = SwipeDirection.None;
            }
        }
    }
}
