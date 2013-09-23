package com.larphoid.langameexample;

public class motionEvent {
	float x;
	float y;

	public motionEvent() {
	}

	public motionEvent(final float x, final float y) {
		this.x = x;
		this.y = y;
	}

	public void multiply(final float mult) {
		this.x *= mult;
		this.y *= mult;
	}
}
