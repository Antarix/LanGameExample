package com.larphoid.langameexample;

import java.net.DatagramPacket;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.text.InputFilter;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.larphoid.langameexample.ColorPickerDialog.OnColorChangedListener;
import com.larphoid.lanudpcomm.ClientEventHandler;
import com.larphoid.lanudpcomm.ClientInviteHandler;
import com.larphoid.lanudpcomm.LanUDPComm;

public class ActivityMain extends Activity implements OnClickListener, OnColorChangedListener, OnItemClickListener, ClientInviteHandler, ClientEventHandler {
	// private static final String TAG = "com.larphoid.LanGameExample";
	private static final int DISCOVERY_PORT = 20193;
	private static final int GAME_PORT = 2345;
	private static final int MAXPACKAGESIZE = 0x100;
	private static final float FLINGVELOCITYSCALE = 0.05f;
	private static final String PREFS_NAME = "prefs";
	private static final String PREF_NAME = "name";
	private static final String PREF_COLOR = "color";

	private static final int MENU_NAME = Menu.FIRST + 0;
	private static final int MENU_COLOR = Menu.FIRST + 1;
	private static final int[] TO_CLIENTS = new int[] {
		R.id.clientname
	};
	private static final int[] TO_MESSAGES = new int[] {
		R.id.timestamp,
		R.id.from,
		R.id.messagetext
	};

	private float radius;
	private float canvasWidth, canvasHeight;
	private List<motionEvent> localEvents = new ArrayList<motionEvent>();
	private List<motionEvent> clientEvents = new ArrayList<motionEvent>();
	private ImageButton btLogin;
	private ColorPickerDialog colorPicker;
	private DrawStuff drawstuff;
	private String myName;
	private int myColor;
	private LanUDPComm lanUdpComm;
	private ByteBuffer eventBuffer;
	private LinearLayout mainwindow;
	private ListView clientsList;
	private ListView messagewindow;
	private Point displaySize = new Point();
	private Client client = new Client();
	private SimpleAdapter clientsAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		loadPreferences();
		mainwindow = (LinearLayout) findViewById(R.id.mainwindow);
		clientsList = (ListView) findViewById(R.id.clientlist);
		clientsList.setOnItemClickListener(this);
		messagewindow = (ListView) findViewById(R.id.messageswindow);
		btLogin = (ImageButton) findViewById(R.id.button_login);
		btLogin.setOnClickListener(this);
		lanUdpComm = new LanUDPComm(this, null, DISCOVERY_PORT, GAME_PORT, MAXPACKAGESIZE, this, this, myName, true);
		clientsAdapter = new SimpleAdapter(this, lanUdpComm.getClientsData(), R.layout.clientitem, LanUDPComm.FROM_CLIENTS, TO_CLIENTS);
		lanUdpComm.setClientsAdapter(clientsAdapter);
		clientsList.setAdapter(clientsAdapter);
		messagewindow.setAdapter(lanUdpComm.getMessagesAdapter(this, R.layout.messageitem, TO_MESSAGES));
		colorPicker = new ColorPickerDialog(this, this, myColor);
		displaySize.x = getResources().getDisplayMetrics().widthPixels;
		displaySize.y = getResources().getDisplayMetrics().heightPixels;
		radius = 16f * ((float) displaySize.x / 800f);
		drawstuff = new DrawStuff(this);
		drawstuff.setVisibility(View.GONE);
		getWindow().addContentView(drawstuff, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		lanUdpComm.inviteClientForConnection(position, null, new String[] {
			String.valueOf(displaySize.x),
			String.valueOf(displaySize.y),
			String.valueOf(myColor)
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_NAME, 0, R.string.menu_name);
		menu.add(0, MENU_COLOR, 0, R.string.menu_color);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return !lanUdpComm.isConnected();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_NAME:
			changeMyDisplayName();
			break;
		case MENU_COLOR:
			colorPicker.show();
			break;
		}
		return true;
	}

	@Override
	public boolean onSearchRequested() {
		lanUdpComm.sendDiscoveryRequest(myName);
		return false;
	}

	@Override
	public void onBackPressed() {
		if (lanUdpComm.stopConnection()) {
			runOnUiThread(stopGame);
			return;
		}
		lanUdpComm.cleanup();
		super.onBackPressed();
	}

	@Override
	protected void onDestroy() {
		System.exit(0);
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.button_login:
			lanUdpComm.sendDiscoveryToAllIps(myName);
			break;
		}
	}

	@Override
	public String[] onInviteAccept() {
		final String[] data = new String[] {
			String.valueOf(displaySize.x),
			String.valueOf(displaySize.y),
			String.valueOf(myColor)
		};
		return data;
	}

	@Override
	public void onStartConnection(final String[] data, final int offset, final DatagramPacket pack) {
		startGame(data, offset, pack);
	}

	@Override
	public void onClientAccepted(String[] data, int offset, DatagramPacket pack) {
		startGame(data, offset, pack);
	}

	@Override
	public void onClientEvent(final byte[] data, final int offset, final int dataLength, final DatagramPacket pack) {
		if (clientEvents.size() > 16) clientEvents.remove(0);
		eventBuffer = ByteBuffer.wrap(data);
		eventBuffer.position(offset);
		final motionEvent motionevent = new motionEvent();
		motionevent.x = eventBuffer.getFloat() * canvasWidth / client.canvasWidth;
		motionevent.y = eventBuffer.getFloat() * canvasHeight / client.canvasHeight;
		clientEvents.add(motionevent);
		drawstuff.postInvalidate();
	}

	@Override
	public void onClientEndConnection(final DatagramPacket pack) {
		lanUdpComm.onClientEndConnection(pack);
		runOnUiThread(stopGame);
	}

	@Override
	public void onClientNotResponding(DatagramPacket pack) {
		lanUdpComm.onClientNotResponding(pack);
		runOnUiThread(stopGame);
	}

	@Override
	public void colorChanged(int color) {
		myColor = color;
		savePreferences();
	}

	// -----------------------------------------------------------------------------------------------------------------------------
	// ------------------------------------------------------- CLASSES and METHODS -------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------------------

	private class DrawStuff extends View implements OnTouchListener, OnGestureListener {
		private boolean ismoving = false;
		private float bounceWidth, bounceHeight;
		private Paint paintLocal, paintClient;
		private motionEvent position = new motionEvent();
		private motionEvent velocity = new motionEvent();
		private GestureDetector gestureDetector;
		private motionEvent motionevent;
		private Handler mHandler = new Handler();

		public DrawStuff(Context context) {
			super(context);
			setKeepScreenOn(true);
			setOnTouchListener(this);
			gestureDetector = new GestureDetector(ActivityMain.this, this);
			paintLocal = new Paint();
			paintLocal.setStyle(Paint.Style.FILL);
			paintLocal.setColor(myColor);
			paintClient = new Paint();
			paintClient.setStyle(Paint.Style.FILL);
			paintClient.setColor(0xff800000);
		}

		@Override
		protected void onSizeChanged(int neww, int newh, int oldw, int oldh) {
			canvasWidth = (float) neww;
			canvasHeight = (float) newh;
			bounceWidth = canvasWidth - radius;
			bounceHeight = canvasHeight - radius;
			super.onSizeChanged(neww, newh, oldw, oldh);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			canvas.drawColor(0xFFFFFFFF);
			if (localEvents.size() > 16) localEvents.remove(0);
			final int localeventssize = localEvents.size();
			for (int i = 0; i < localeventssize; i++) {
				try {
					motionevent = localEvents.get(i);
					canvas.drawCircle(motionevent.x, motionevent.y, radius, paintLocal);
				} catch (Exception e) {
				}
			}
			final int clienteventssize = clientEvents.size();
			for (int i = 0; i < clienteventssize; i++) {
				try {
					motionevent = clientEvents.get(i);
					canvas.drawCircle(motionevent.x, motionevent.y, radius, paintClient);
				} catch (Exception e) {
				}
			}
			super.onDraw(canvas);
		}

		@Override
		public boolean onTouch(View v, MotionEvent e) {
			if (lanUdpComm.isConnected()) return gestureDetector.onTouchEvent(e);
			return true;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			ismoving = false;
			sendGameEvent(e);
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			position.x = e2.getX();
			position.y = e2.getY();
			velocity.x = velocityX * FLINGVELOCITYSCALE;
			velocity.y = velocityY * FLINGVELOCITYSCALE;
			ismoving = true;
			mHandler.post(fling);
			return true;
		}

		@Override
		public void onLongPress(MotionEvent e) {
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			ismoving = false;
			sendGameEvent(e2);
			return true;
		}

		@Override
		public void onShowPress(MotionEvent e) {
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			ismoving = false;
			sendGameEvent(e);
			return true;
		}

		public void setDrawingColors() {
			paintLocal.setColor(myColor);
			paintClient.setColor(client.color);
		}

		private void sendGameEvent(final float x, final float y) {
			if (localEvents.size() > 16) localEvents.remove(0);
			final motionEvent e = new motionEvent(x, y);
			final ByteBuffer buffer = lanUdpComm.GetClientEventBuffer();
			try {
				buffer.putFloat(e.x);
				buffer.putFloat(e.y);
			} catch (BufferOverflowException e1) {
				e1.printStackTrace();
			}
			lanUdpComm.sendClientPacket();
			localEvents.add(e);
			postInvalidate();
		}

		private void sendGameEvent(MotionEvent e) {
			sendGameEvent(e.getX(), e.getY());
		}

		private Runnable fling = new Runnable() {
			@Override
			public void run() {
				if (ismoving && lanUdpComm.isConnected()) {
					if (Math.abs(velocity.x) > 1f && Math.abs(velocity.y) > 1f) {
						position.x += velocity.x;
						position.y += velocity.y;
						if (position.x < radius) {
							position.x = radius + (radius - position.x);
							velocity.x = -velocity.x;
						} else if (position.x > bounceWidth) {
							position.x = (bounceWidth) + (bounceWidth - position.x);
							velocity.x = -velocity.x;
						}
						if (position.y < radius) {
							position.y = radius + (radius - position.y);
							velocity.y = -velocity.y;

						} else if (position.y > bounceHeight) {
							position.y = (bounceHeight) + (bounceHeight - position.y);
							velocity.y = -velocity.y;
						}
						velocity.x *= 0.9f;
						velocity.y *= 0.9f;
						sendGameEvent(position.x, position.y);
						mHandler.postDelayed(fling, 50);
					} else {
						ismoving = false;
					}
				}
			}
		};
	}

	private void loadPreferences() {
		final SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		myName = preferences.getString(PREF_NAME, Build.MODEL + "_" + Secure.getString(getContentResolver(), Secure.ANDROID_ID));
		myColor = preferences.getInt(PREF_COLOR, 0xFF00FF00);
	}

	private void savePreferences() {
		final SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor prefs = preferences.edit();
		prefs.putString(PREF_NAME, myName);
		prefs.putInt(PREF_COLOR, myColor);
		prefs.commit();
	}

	private void changeMyDisplayName() {
		final EditText edit = new EditText(this);
		edit.setSingleLine(true);
		edit.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		edit.setPadding(16, 16, 16, 16);
		edit.setText(myName);
		edit.setHint(R.string.menu_name);
		edit.selectAll();
		edit.setFilters(new InputFilter[] {
			new InputFilter.LengthFilter(50)
		});
		new AlertDialog.Builder(this).setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setTitle(R.string.menu_name).setView(edit).setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				myName = edit.getText().toString();
				savePreferences();
				lanUdpComm.sendDiscoveryRequest(myName);
			}
		}).show();
	}

	private void startGame(final String data[], final int offset, final DatagramPacket pack) {
		localEvents.clear();
		clientEvents.clear();
		client.canvasWidth = Float.valueOf(data[offset + 0]);
		client.canvasHeight = Float.valueOf(data[offset + 1]);
		client.color = Integer.valueOf(data[offset + 2]);
		runOnUiThread(startGame);
		drawstuff.setDrawingColors();
		drawstuff.postInvalidate();
	}

	private Runnable startGame = new Runnable() {
		@Override
		public void run() {
			mainwindow.setVisibility(View.GONE);
			drawstuff.setVisibility(View.VISIBLE);
		}
	};

	private Runnable stopGame = new Runnable() {
		@Override
		public void run() {
			drawstuff.setVisibility(View.GONE);
			mainwindow.setVisibility(View.VISIBLE);
			localEvents.clear();
			clientEvents.clear();
		}
	};
}
