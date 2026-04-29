package com.winlator.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.collection.MutableObjectList;

import com.winlator.core.Callback;
import com.winlator.renderer.GLRenderer;
import com.winlator.xserver.XServer;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class XServerView extends GLSurfaceView {
    private final GLRenderer renderer;
    // private final ArrayList<Callback<MotionEvent>> mouseEventCallbacks = new ArrayList<>();
    private final XServer xServer;
    private final Object renderThrottleLock = new Object();
    private final Handler renderThrottleHandler = new Handler(Looper.getMainLooper());
    private int frameRateLimit = 0;
    private long minRenderIntervalMs = 0L;
    private long lastRenderRequestUptimeMs = 0L;
    private boolean renderRequestScheduled = false;
    private final Runnable throttledRenderRunnable = () -> {
        synchronized (renderThrottleLock) {
            renderRequestScheduled = false;
            lastRenderRequestUptimeMs = SystemClock.uptimeMillis();
        }
        XServerView.super.requestRender();
    };

    public XServerView(Context context, XServer xServer) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        this.xServer = xServer;
        renderer = new GLRenderer(this, xServer);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        // setOnFocusChangeListener((view, gainFocus) -> {
        //     Log.d("XServerView", "onFocusChange: " + gainFocus + ", isMe: " + (view == this));
        // });
        //
        // requestFocus();
    }
    public XServer getxServer() {
        return xServer;
    }
    // public void onRelease() {
    //     releasePointerCapture();
    //     clearPointerEventListeners();
    // }

    public GLRenderer getRenderer() {
        return renderer;
    }

    public int getFrameRateLimit() {
        synchronized (renderThrottleLock) {
            return frameRateLimit;
        }
    }

    public void setFrameRateLimit(int frameRateLimit) {
        final boolean shouldRecomputeRender;
        synchronized (renderThrottleLock) {
            boolean hadScheduledRender = renderRequestScheduled;
            this.frameRateLimit = Math.max(0, frameRateLimit);
            minRenderIntervalMs = this.frameRateLimit > 0
                ? Math.max(1L, Math.round(1000f / (float) this.frameRateLimit))
                : 0L;

            if (hadScheduledRender) {
                renderThrottleHandler.removeCallbacks(throttledRenderRunnable);
                renderRequestScheduled = false;
            }
            shouldRecomputeRender = this.frameRateLimit == 0 || hadScheduledRender;
        }

        if (shouldRecomputeRender) {
            requestRender();
        }
    }

    @Override
    public void requestRender() {
        long delayMs = 0L;

        synchronized (renderThrottleLock) {
            if (frameRateLimit <= 0) {
                super.requestRender();
                return;
            }

            long now = SystemClock.uptimeMillis();
            long remainingDelay = minRenderIntervalMs - (now - lastRenderRequestUptimeMs);
            if (!renderRequestScheduled && remainingDelay <= 0L) {
                lastRenderRequestUptimeMs = now;
            } else {
                if (renderRequestScheduled) {
                    return;
                }
                renderRequestScheduled = true;
                delayMs = Math.max(1L, remainingDelay);
            }
        }

        if (delayMs == 0L) {
            super.requestRender();
        } else {
            renderThrottleHandler.postDelayed(throttledRenderRunnable, delayMs);
        }
    }

    // public void addPointerEventListener(Callback<MotionEvent> listener) {
    //     mouseEventCallbacks.add(listener);
    // }
    // public void removePointerEventListener(Callback<MotionEvent> listener) {
    //     mouseEventCallbacks.remove(listener);
    // }
    // public void clearPointerEventListeners() {
    //     mouseEventCallbacks.clear();
    // }
    // private void emitPointerEvent(MotionEvent event) {
    //     for (Callback<MotionEvent> listener : mouseEventCallbacks) {
    //         listener.call(event);
    //     }
    // }

    // @Override
    // public boolean onCapturedPointerEvent(MotionEvent event) {
    //     Log.d("XServerView", "onCapturedPointerEvent:\n\t" + event);
    //     emitPointerEvent(event);
    //     return true;
    // }


    // @Override
    // public boolean dispatchGenericMotionEvent(MotionEvent event) {
    //     Log.d("XServerView", "dispatchGenericMotionEvent:\n\t" + event);
    //     return super.dispatchGenericMotionEvent(event);
    // }
    //
    // @Override
    // protected boolean dispatchGenericPointerEvent(MotionEvent event) {
    //     Log.d("XServerView", "dispatchGenericPointerEvent:\n\t" + event);
    //     return super.dispatchGenericPointerEvent(event);
    // }
    //
    // @Override
    // protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
    //     super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    //     Log.d("XServerView", "Focus changed: " + gainFocus);
    // }
    //
    // @Override
    // public boolean dispatchCapturedPointerEvent(MotionEvent event) {
    //     Log.d("XServerView", "dispatchCapturedPointerEvent:\n\t" + event);
    //     emitPointerEvent(event);
    //     return super.dispatchCapturedPointerEvent(event);
    // }
    //
    // @Override
    // public void onPointerCaptureChange(boolean hasCapture) {
    //     super.onPointerCaptureChange(hasCapture);
    //     Log.d("XServerView", "onPointerCaptureChange: " + hasCapture);
    // }
    //
    // @Override
    // public void onWindowFocusChanged(boolean hasWindowFocus) {
    //     super.onWindowFocusChanged(hasWindowFocus);
    //     if (hasWindowFocus) {
    //         requestPointerCapture();
    //     } else {
    //         releasePointerCapture();
    //     }
    // }
}
