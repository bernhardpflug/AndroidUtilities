package at.bernhardpflug.android;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;


/**
 * View that renders svg as background by using svg rendering from
 * https://code.google.com/p/androidsvg/
 *
 * Other than SvgImageView from the svg library this view adjusts the SVG to the size of the view to provide pixel perfect rendering.
 *
 * @author Bernhard Pflug
 *
 */
public class SvgView extends View {

    private SVG svg;

    public SvgView(Context context) {
        this(context, null);
    }

    public SvgView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, new int[] { R.attr.svg });

        int svgRes = a.getResourceId(0, -1);

        if (svgRes != -1) {
            setSvgResource(svgRes);
        }

        a.recycle();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w > 0 && h > 0 && svg != null) {
            svg.setDocumentWidth(w);
            svg.setDocumentHeight(h);
            // unnecessary as default option centers svg as desired
            // svg.setDocumentPreserveAspectRatio(PreserveAspectRatio.TOP);
            Bitmap svgBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(svgBitmap);
            // Render our document onto our canvas
            svg.renderToCanvas(bitmapCanvas);
            setBackgroundDrawable(new BitmapDrawable(getResources(), svgBitmap));
        }
    }

    public void setSvgResource(int svgResId) {
        try {
            svg = SVG.getFromResource(getContext(), svgResId);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // API 11 METHOD!
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
        } catch (SVGParseException e) {
            e.printStackTrace();
        }
    }

}
