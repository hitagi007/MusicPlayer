package itto.pl.music_visualizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;

public class VerticalBarVisualizer extends BaseVisualizer {
    private static final String TAG = "PL_itto." + VerticalBarVisualizer.class.getSimpleName();
    private float mDensity = 50;
    private int mGap;

    public VerticalBarVisualizer(Context context) {
        super(context);
    }

    public VerticalBarVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VerticalBarVisualizer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public VerticalBarVisualizer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void init() {
        mDensity = 50;
        mGap = 4;
        mPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Sets the density to the Bar visualizer i.e the number of bars
     * to be displayed. Density can vary from 10 to 256.
     * by default the value is set to 50.
     *
     * @param density
     */
    public void setDensity(int density) {
        mDensity = density;
        if (density > 256) {
            mDensity = 256;
        } else if (density < 10) {
            mDensity = 10;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Log.d(TAG, "onDraw: ");
        if (mBytes != null) {
            float barWidth = getWidth() / mDensity;
            float div = mBytes.length / mDensity;
            mPaint.setStrokeWidth(barWidth - mGap);
            for (int i = 0; i < mDensity; i++) {
                int bytePosition = (int) Math.ceil(i * div);
                int top = getHeight() +
                        ((byte) (Math.abs(mBytes[bytePosition]) + 128)) * getHeight() / 128;
                float barX = (i * barWidth) + (barWidth / 2);
                canvas.drawLine(barX, getHeight(), barX, top, mPaint);
            }
        }
        super.onDraw(canvas);
    }
}
