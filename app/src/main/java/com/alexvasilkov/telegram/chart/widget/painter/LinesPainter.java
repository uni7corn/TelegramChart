package com.alexvasilkov.telegram.chart.widget.painter;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

import com.alexvasilkov.telegram.chart.domain.Chart;
import com.alexvasilkov.telegram.chart.domain.Chart.Source;
import com.alexvasilkov.telegram.chart.utils.ChartMath;
import com.alexvasilkov.telegram.chart.utils.Range;
import com.alexvasilkov.telegram.chart.widget.style.ChartStyle;

class LinesPainter extends Painter {

    private final Paint pathPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private final Paint selectionPaint = new Paint();
    private final Paint pointPaint = new Paint(ChartStyle.PAINT_FLAGS);
    private float pointRadius;

    private final Path path = new Path();
    private float[] pathsPoints;
    private float[] pathsPointsTransformed;

    LinesPainter(Chart chart, ChartStyle style, boolean independentSources) {
        this(chart, style);

        if (independentSources) {
            final int size = chart.x.length;
            final int sourcesCount = chart.sources.length;

            final int[] max = new int[sourcesCount];
            int totalMax = Integer.MIN_VALUE;

            for (int l = 0; l < sourcesCount; l++) {
                final Source source = chart.sources[l];

                int maxValue = Integer.MIN_VALUE;

                for (int i = 0; i < size; i++) {
                    maxValue = maxValue < source.y[i] ? source.y[i] : maxValue;
                }

                // It is not allowed to have max value lower than 1
                max[l] = Math.max(maxValue, 1);
                totalMax = totalMax < max[l] ? max[l] : totalMax;
            }

            for (int l = 0; l < sourcesCount; l++) {
                final float scale = totalMax / (float) max[l];

                final int factor10 = (int) Math.floor(Math.max(0f, Math.log10(scale)));
                final int roundFactor = (int) Math.pow(10, factor10);
                sourcesScales[l] = (float) Math.floor(scale / roundFactor) * roundFactor;
            }
        }
    }

    LinesPainter(Chart chart, ChartStyle style) {
        super(chart);

        final int points = chart.x.length;
        pathsPoints = new float[4 * (points - 1)];
        pathsPointsTransformed = new float[4 * (points - 1)];


        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setStyle(Paint.Style.FILL);

        applyStyle(style);
    }

    private void applyStyle(ChartStyle style) {
        pathPaint.setStrokeWidth(style.lineWidth);

        selectionPaint.setStrokeWidth(style.selectionWidth);
        selectionPaint.setColor(style.selectionColor);

        pointPaint.setColor(style.pointColor);
        pointRadius = style.pointRadius;
    }


    @Override
    public void calculateYRange(
            Range yRange,
            int from,
            int to,
            boolean[] sourcesStates
    ) {

        // Calculating min and max Y value across all visible sources
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;

        for (int l = 0, size = chart.sources.length; l < size; l++) {
            if (!sourcesStates[l]) {
                continue; // Ignoring invisible sources
            }

            final Source source = chart.sources[l];
            final float valueScale = sourcesScales[l];

            for (int i = from; i <= to; i++) {
                final float value = source.y[i] * valueScale;
                minY = minY > value ? value : minY;
                maxY = maxY < value ? value : maxY;
            }
        }

        if (minY == Float.MAX_VALUE) {
            minY = 0;
        }
        if (maxY == Float.MIN_VALUE) {
            maxY = minY + 1;
        }

        yRange.set(minY, maxY);
    }

    @Override
    public void draw(
            Canvas canvas,
            Rect chartPos,
            Matrix matrix,
            int from,
            int to,
            float[] sourcesStates,
            int selected,
            boolean simplified
    ) {

        // Drawing selected point line if withing visible range
        if (from <= selected && selected <= to) {
            float posX = ChartMath.mapX(matrix, selected);
            canvas.drawLine(posX, chartPos.top, posX, chartPos.bottom, selectionPaint);
        }

        for (int l = 0, size = chart.sources.length; l < size; l++) {
            final float state = sourcesStates[l];
            final Source source = chart.sources[l];
            final float valuesScale = sourcesScales[l];

            if (state == 0f) {
                continue; // Ignoring invisible sources
            }

            pathPaint.setColor(source.color);
            pathPaint.setAlpha(toAlpha(state));

            if (simplified) {
                // Drawing a set of lines is much faster than drawing a path
                drawAsLines(canvas, matrix, source.y, valuesScale, from, to);
            } else {
                // But a path looks better since it smoothly joins the lines
                drawAsPath(canvas, matrix, source.y, valuesScale, from, to);
            }
        }

        if (selected == -1) {
            return;
        }

        for (int l = 0, size = chart.sources.length; l < size; l++) {
            final float state = sourcesStates[l];
            final Source source = chart.sources[l];
            final float valuesScale = sourcesScales[l];

            if (state == 0f) {
                continue; // Ignoring invisible sources
            }

            pathPaint.setColor(source.color);
            pathPaint.setAlpha(toAlpha(state));
            // Point's alpha should change much slower than main path
            pointPaint.setAlpha(toAlpha((float) Math.sqrt(Math.sqrt(state))));

            drawSelected(canvas, matrix, selected, source.y[selected], valuesScale);
        }
    }

    private void drawAsPath(
            Canvas canvas, Matrix matrix,
            int[] values, float valueScale,
            int from, int to
    ) {
        path.reset();

        for (int i = from; i <= to; i++) {
            float value = values[i] * valueScale;
            if (i == from) {
                path.moveTo(i, value);
            } else {
                path.lineTo(i, value);
            }
        }

        path.transform(matrix);

        canvas.drawPath(path, pathPaint);
    }

    private void drawAsLines(
            Canvas canvas, Matrix matrix,
            int[] values, float valueScale,
            int from, int to
    ) {
        final float[] points = pathsPoints;

        for (int i = from; i < to; i++) {
            points[4 * i] = i;
            points[4 * i + 1] = values[i] * valueScale;
            points[4 * i + 2] = i + 1;
            points[4 * i + 3] = values[i + 1] * valueScale;
        }

        final int offset = 4 * from;
        final int count = 2 * (to - from);

        matrix.mapPoints(pathsPointsTransformed, offset, points, offset, count);

        canvas.drawLines(pathsPointsTransformed, offset, 2 * count, pathPaint);
    }


    private void drawSelected(Canvas canvas, Matrix matrix, int x, int y, float yScale) {
        float posX = ChartMath.mapX(matrix, x);
        float posY = ChartMath.mapY(matrix, y * yScale);

        canvas.drawCircle(posX, posY, pointRadius, pointPaint);
        canvas.drawCircle(posX, posY, pointRadius, pathPaint);
    }

}
