package at.bernhardpflug.android;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import java.util.ArrayList;

/**
 * Custom view that takes two views whereas by default the ground view consumes
 * the entire screen. If the user swipes down the sky view gets visible.
 * When stopping the touch event in-between the view snaps to the appropriate
 * view.
 *
 * @author Bernhard Pflug
 * 
 */
public class VerticalViewPager extends ViewGroup {

	/* **************************************************************************
	 * *********************************VARIABLES********************************
	 */

	public interface OnViewSwitchedListener {
		public void onViewSwitched(VerticalViewPager verticalViewPager, State state);
	}

	public enum State {
		OPEN, CLOSED
	}

	private State currentState = State.CLOSED;

	// static threshold values
	private final int touchSlop;
	private final int maximumVelocity, minimumVelocity;

	private View skyView, groundView;

	private Scroller scroller;
	private VelocityTracker velocityTracker;

	// touch variables
	private boolean verticalScrolling;
	private float lastTouchY;

	private boolean pagingInProgress = false;

	// listener
	private ArrayList<OnViewSwitchedListener> onViewSwitchedListeners = new ArrayList<OnViewSwitchedListener>();

	/* **************************************************************************
	 * ********************************CONSTRUCTOR*******************************
	 */

	public VerticalViewPager(Context context) {
		this(context, null);
	}

	public VerticalViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);

		touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		maximumVelocity = ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity();
		minimumVelocity = ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();

		scroller = new Scroller(getContext(), new DecelerateInterpolator());
		velocityTracker = VelocityTracker.obtain();

		TypedArray a = context.obtainStyledAttributes(attrs, new int[] { R.attr.groundView, R.attr.skyView });

		int groundResource = a.getResourceId(0, -1);
		int skyResource = a.getResourceId(1, -1);

		if (groundResource == -1 || skyResource == -1) {
			throw new IllegalArgumentException("Missing either sky or ground view reference as layout attribute");
		}

		setSkyView(skyResource);
		setGroundView(groundResource);
	}

	/* **************************************************************************
	 * *****************************VIEW_GROUP_METHODS***************************
	 */

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int width = r - l;
		final int height = b - t;

		skyView.layout(0, 0, width, skyView.getMeasuredHeight());
		groundView.layout(0, skyView.getMeasuredHeight(), width, height + skyView.getMeasuredHeight());

		// sync current scroll
		scrollTo(0, getY(currentState));
	}

	/**
	 * Needed to let content of subviews appear
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		int width = getDefaultSize(0, widthMeasureSpec);
		int height = getDefaultSize(0, heightMeasureSpec);
		setMeasuredDimension(width, height);

        int horizontalPadding = getPaddingLeft()+getPaddingRight();
        int verticalPadding = getPaddingTop()+getPaddingBottom();

		final int contentWidth = getChildMeasureSpec(widthMeasureSpec, horizontalPadding, width);
		final int contentHeight = getChildMeasureSpec(heightMeasureSpec, verticalPadding, height);

		groundView.measure(contentWidth, contentHeight);

		final int skyHeight = getChildMeasureSpec(heightMeasureSpec, verticalPadding, skyView.getLayoutParams().height);
		skyView.measure(contentWidth, skyHeight);
	}

	/*
	 * Monitor touch events passed down to the children and intercept as soon as it is determined we are scrolling. This allows child views to still receive touch events if they are interactive (i.e. Buttons)
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {

		switch (event.getAction()) {

		case MotionEvent.ACTION_DOWN:

			// Stop any scrolling in progress
			if (!scroller.isFinished()) {
				scroller.abortAnimation();
			}

			// Reset the velocity tracker
			velocityTracker.clear();
			velocityTracker.addMovement(event);

			// Save the initial touch point
			lastTouchY = event.getY();
			break;

		case MotionEvent.ACTION_MOVE:
			final float y = event.getY();
			final int yDiff = (int) Math.abs(y - lastTouchY);

			// Verify that difference is enough to be scrolling
			if (yDiff > touchSlop) {
				verticalScrolling = true;
				
				// disable intercept touch events for parent views to prevent touch event loss
				requestDisallowInterceptTouchEvent(true);
				
				velocityTracker.addMovement(event);
				// Start capturing events ourselves
				return true;
			}
			break;

		case MotionEvent.ACTION_CANCEL:

		case MotionEvent.ACTION_UP:
			verticalScrolling = false;
			velocityTracker.clear();
			break;
		}

		return super.onInterceptTouchEvent(event);
	}

	/*
	 * Feed all touch events we receive to the detector for processing.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		velocityTracker.addMovement(event);

		switch (event.getAction()) {

		case MotionEvent.ACTION_DOWN:
			// We've already stored the initial point,
			// but if we got here a child view didn't capture
			// the event, so we need to.
			return true;

		case MotionEvent.ACTION_MOVE:
			final float y = event.getY();
			float deltaY = lastTouchY - y;

			// Check for slop on direct events
			if (!verticalScrolling && Math.abs(deltaY) > touchSlop) {
				verticalScrolling = true;
				
				// disable intercept touch events for parent views to prevent touch event loss
				requestDisallowInterceptTouchEvent(true);
			}
			if (verticalScrolling) {

				// Scroll view only if it is between open and closed coordinates
				if (getScrollY() + deltaY <= getY(State.CLOSED) && getScrollY() + deltaY >= getY(State.OPEN)) {
					scrollBy(0, (int) deltaY);
				}

				// Update the last touch event
				lastTouchY = y;
			}
			break;

		case MotionEvent.ACTION_CANCEL:

			verticalScrolling = false;
			snapToDestination();
			break;

		case MotionEvent.ACTION_UP:

			verticalScrolling = false;
			// Compute the current velocity and start a fling if it is above
			// the minimum threshold.
			velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
			int velocityY = (int) velocityTracker.getYVelocity();

			if (velocityY < -minimumVelocity && currentState != State.CLOSED) {
				snapToState(State.CLOSED);
			} else if (velocityY > minimumVelocity && currentState != State.OPEN) {
				snapToState(State.OPEN);
			} else {
				snapToDestination();
			}
			break;
		}
		return super.onTouchEvent(event);
	}

	@Override
	public void computeScroll() {
		if (scroller.computeScrollOffset()) {
			// This is called at drawing time by ViewGroup. We use
			// this method to keep the snap animation going through
			// to completion.
			int x = scroller.getCurrX();
			int y = scroller.getCurrY();

			// scroll view
			scrollTo(x, y);

			pagingInProgress = true;

			// Keep on drawing until the animation has finished.
			postInvalidate();
		} else if (scroller.isFinished() && pagingInProgress) {
			pagingInProgress = false;

			for (OnViewSwitchedListener listener : onViewSwitchedListeners) {
				listener.onViewSwitched(this, getState());
			}
		}
	}

	/* **************************************************************************
	 * *******************************PUBLIC_METHODS*****************************
	 */

	public void setSkyView(View skyView) {
		addView(skyView);
		this.skyView = skyView;
	}

	public void setSkyView(int resourceId) {
		setSkyView(LayoutInflater.from(getContext()).inflate(resourceId, null));
	}

	public void setGroundView(View viewAbove) {
		addView(viewAbove);
		this.groundView = viewAbove;
	}

	public void setGroundView(int resourceId) {
		setGroundView(LayoutInflater.from(getContext()).inflate(resourceId, null));
	}

	public State getState() {
		return currentState;
	}

	/**
	 * Animated movement to given state
	 */
	public void snapToState(State state) {
		if (!scroller.isFinished()) {
			return;
		}

		if (state == State.OPEN) {
			scroller.startScroll(0, getScrollY(), 0, getY(State.OPEN) - getScrollY());
		} else {
			scroller.startScroll(0, getScrollY(), 0, getY(State.CLOSED) - getScrollY());
		}
		currentState = state;

		invalidate();
	}

	public void addOnViewSwitchedListener(OnViewSwitchedListener onViewSwitchedListener) {
		this.onViewSwitchedListeners.add(onViewSwitchedListener);
	}

	public void removeOnViewSwitchedListener(OnViewSwitchedListener onViewSwitchedListener) {
		this.onViewSwitchedListeners.remove(onViewSwitchedListener);
	}

	/* **************************************************************************
	 * ******************************PRIVATE_METHODS*****************************
	 */

	/**
	 * Decides on current scroll position (above/below the half) whether to open or close
	 */
	private void snapToDestination() {

		if (getPercentOpen() >= 0.5f) {
			snapToState(State.OPEN);
		} else {
			snapToState(State.CLOSED);
		}
	}

	private int getY(State state) {
		if (state == State.OPEN) {
			return 0;
		} else if (state == State.CLOSED) {
			return skyView.getHeight();
		}
		throw new IllegalArgumentException("Unknown state " + state);
	}

	private float getPercentOpen() {
		// absolute range between open and close
		float totalRange = (float) Math.abs(getY(State.CLOSED) - getY(State.OPEN));

		// relative range from current scroll inside total range
		float scrollDiff = (float) (getScrollY() - Math.min(getY(State.OPEN), getY(State.CLOSED)));

		if (getY(State.CLOSED) < getY(State.OPEN)) {
			return scrollDiff / totalRange;
		} else {
			return 1.0f - scrollDiff / totalRange;
		}
	}
}
