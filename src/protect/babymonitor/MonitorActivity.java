/**
 * This file is part of the Protect Baby Monitor.
 *
 * Protect Baby Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Protect Baby Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Protect Baby Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package protect.babymonitor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import protect.babymonitor.PromptDialog.PromptDialogListener;
import zxing.Contents;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

public class MonitorActivity extends Activity implements PromptDialogListener{
	final String TAG = "BabyMonitor";
	private short threshold = 1000; //this threshold should be adjustable at the parent device
	private int thresholdMax = 10000;
	private int heartbeatTimeout = 10000;
	private long lastHeartBeat = 0;
	String password = "";
	byte[] salt ;
	
	NsdManager _nsdManager;

	NsdManager.RegistrationListener _registrationListener;

	Thread _serviceThread;

	private void serviceConnection(Socket socket) throws IOException {
		MonitorActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final TextView statusText = (TextView) findViewById(R.id.textStatus);
				statusText.setText(R.string.streaming);
			}
		});

		final int frequency = 11025;
		final int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
		final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

		final int bufferSize = AudioRecord.getMinBufferSize(frequency,
				channelConfiguration, audioEncoding);
		final AudioRecord audioRecord = new AudioRecord(
				MediaRecorder.AudioSource.MIC, frequency, channelConfiguration,
				audioEncoding, bufferSize);

		final int byteBufferSize = bufferSize * 2;
		final byte[] buffer = new byte[byteBufferSize];
		final short[] buffer2 = new short[bufferSize];

		try {
			audioRecord.startRecording();

//			socket.setKeepAlive(true);
			
			   final OutputStream out = socket.getOutputStream();

	            /* Derive the key, given password and salt. */
	            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1And8bit");
	            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
	            SecretKey tmp = factory.generateSecret(spec);
	            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
	/* Encrypt the message. */
	            Cipher encipher = Cipher.getInstance("AES/CBC/PKCS5Padding");


	                encipher.init(Cipher.ENCRYPT_MODE, secret);
	            AlgorithmParameters params = encipher.getParameters();

			
			
	            CipherOutputStream cos = new CipherOutputStream(out, encipher);

			socket.setSendBufferSize(byteBufferSize);
			Log.d(TAG, "Socket send buffer size: " + socket.getSendBufferSize());

			while (socket.isConnected()
					&& Thread.currentThread().isInterrupted() == false) {
				final int read = audioRecord.read(buffer2, 0, bufferSize);

				int foundPeak = searchThreshold(buffer2, threshold);
				if (foundPeak > -1) { // found signal
					lastHeartBeat = System.currentTimeMillis();
										// record signal
					//create a timeout in the searchTrheshold check.
					try {
						 byte[] byteBuffer =ShortToByte(buffer2,read);
//						 out.write(byteBuffer);
			                cos.write(byteBuffer);

//						out.write(buffer, 0, read);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {// count the time
						// don't save signal
					if(lastHeartBeat + heartbeatTimeout - 1000 < System.currentTimeMillis()) {
						//send heartbeat
						lastHeartBeat = System.currentTimeMillis();
//						out.write("beat".getBytes());
		                cos.write("beat".getBytes());

					}
				}

			}
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		} catch (InvalidKeyException e1) {
			e1.printStackTrace();
		} catch (InvalidKeySpecException e1) {
			e1.printStackTrace();
		} catch (NoSuchPaddingException e1) {
			e1.printStackTrace();
		} finally {
			audioRecord.stop();
		}
	}

	// provided by
	// http://stackoverflow.com/questions/19145213/android-audio-capture-silence-detection
	  byte [] ShortToByte(short [] input, int elements) {
	      int short_index, byte_index;
	      int iterations = elements; //input.length;
	      byte [] buffer = new byte[iterations * 2];

	      short_index = byte_index = 0;

	      for(/*NOP*/; short_index != iterations; /*NOP*/)
	      {
	        buffer[byte_index]     = (byte) (input[short_index] & 0x00FF); 
	        buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);

	        ++short_index; byte_index += 2;
	      }

	      return buffer;
	    }
	
	private int searchThreshold(short[] arr, short thr) {
		int peakIndex;
		int arrLen = arr.length;
		for (peakIndex = 0; peakIndex < arrLen; peakIndex++) {
			if ((arr[peakIndex] >= thr) || (arr[peakIndex] <= -thr)) {
				Log.d(TAG, "peak "+peakIndex +" value "+arr[peakIndex]);
				
				return peakIndex;
			}
		}
		return -1; // not found
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "Baby monitor start");

		_nsdManager = (NsdManager) this.getSystemService(Context.NSD_SERVICE);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);
		
		SeekBar volumeThreshold = (SeekBar) this.findViewById(R.id.volumeThreshold);
		volumeThreshold.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
					threshold = (short) (progress * 0.01 *  thresholdMax) ;
					Log.d(TAG, "Threshold: "+threshold);
			}
		});
		
//		FragmentManager fm = getFragmentManager();
//		PromptDialog dialogFragment = new PromptDialog ();
//		dialogFragment.setPromptDialogListener(this);
//		dialogFragment.show(fm, "Sample Fragment");
		
		password = Util.generatePassword(20);
		Log.d(TAG, "password: "+password);


		final SecureRandom r = new SecureRandom();
		salt = new byte[32];
		r.nextBytes(salt);
		String encodedSalt = Base64.encodeToString(salt, Base64.DEFAULT);
		Log.d(TAG, "salt: "+encodedSalt);

		
		initializeServer();
		updateScreen();
	}

	@Override
	protected void onDestroy() {
		Log.i(TAG, "Baby monitor stop");

		unregisterService();

		if (_serviceThread != null) {
			_serviceThread.interrupt();
			_serviceThread = null;
		}

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.start, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void registerService(final int port) {
		final NsdServiceInfo serviceInfo = new NsdServiceInfo();
		serviceInfo.setServiceName("ProtectBabyMonitor");
		serviceInfo.setServiceType("_babymonitor._tcp.");
		serviceInfo.setPort(port);

		_registrationListener = new NsdManager.RegistrationListener() {
			@Override
			public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
				// Save the service name. Android may have changed it in order
				// to
				// resolve a conflict, so update the name you initially
				// requested
				// with the name Android actually used.
				final String serviceName = nsdServiceInfo.getServiceName();

				Log.i(TAG, "Service name: " + serviceName);

				MonitorActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						final TextView statusText = (TextView) findViewById(R.id.textStatus);
						statusText.setText(R.string.waitingForParent);

						final TextView serviceText = (TextView) findViewById(R.id.textService);
						serviceText.setText(serviceName);

						final TextView portText = (TextView) findViewById(R.id.port);
						portText.setText(Integer.toString(port));
					}
				});
			}

			@Override
			public void onRegistrationFailed(NsdServiceInfo serviceInfo,
					int errorCode) {
				// Registration failed! Put debugging code here to determine
				// why.
				Log.e(TAG, "Registration failed: " + errorCode);
			}

			@Override
			public void onServiceUnregistered(NsdServiceInfo arg0) {
				// Service has been unregistered. This only happens when you
				// call
				// NsdManager.unregisterService() and pass in this listener.

				Log.i(TAG, "Unregistering service");
			}

			@Override
			public void onUnregistrationFailed(NsdServiceInfo serviceInfo,
					int errorCode) {
				// Unregistration failed. Put debugging code here to determine
				// why.

				Log.e(TAG, "Unregistration failed: " + errorCode);
			}
		};

		_nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD,
				_registrationListener);
	}

	/**
	 * Uhregistered the service and assigns the listener to null.
	 */
	private void unregisterService() {
		if (_registrationListener != null) {
			Log.i(TAG, "Unregistering monitoring service");

			_nsdManager.unregisterService(_registrationListener);
			_registrationListener = null;
		}
	}

	@Override
	public void onFinishPromptDialog(String inputText) {
		password = inputText;
		
		initializeServer();
		updateScreen();

	}
	
	private void initializeServer() {
		_serviceThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (Thread.currentThread().isInterrupted() == false) {
					ServerSocket serverSocket = null;

					try {
						// Initialize a server socket on the next available
						// port.
						serverSocket = new ServerSocket(0);

						// Store the chosen port.
						final int localPort = serverSocket.getLocalPort();

						// Register the service so that parent devices can
						// locate the child device
						registerService(localPort);

						// Wait for a parent to find us and connect
						Socket socket = serverSocket.accept();
						Log.i(TAG, "Connection from parent device received");

						// We now have a client connection.
						// Unregister so no other clients will
						// attempt to connect
						serverSocket.close();
						serverSocket = null;
						unregisterService();

						try {
							serviceConnection(socket);
						} finally {
							socket.close();
						}
					} catch (IOException e) {
						Log.e(TAG, "Connection failed", e);
					}

					// If an exception was thrown before the connection
					// could be closed, clean it up
					if (serverSocket != null) {
						try {
							serverSocket.close();
						} catch (IOException e) {
							Log.e(TAG, "Failed to close stray connection", e);
						}
						serverSocket = null;
					}
				}
			}
		});
		_serviceThread.start();
	}
	
	private void updateScreen() {
		
		//TO-DO: generate QR code for listenAcitivity using QZING lib
		
		MonitorActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				
				// ImageView to display the QR code in.  This should be defined in 
				// your Activity's XML layout file
				final ImageView imageView = (ImageView) findViewById(R.id.qrCode);

				String qrData = "Data I want to encode in QR code";
				int qrCodeDimention = 500;

				QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(qrData, null,
				        Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), qrCodeDimention);

				try {
				    Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
				    imageView.setImageBitmap(bitmap);
				} catch (WriterException e) {
				    e.printStackTrace();
				}
				
				final TextView addressText = (TextView) findViewById(R.id.address);

				final WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
				final WifiInfo info = wifiManager.getConnectionInfo();
				final int address = info.getIpAddress();
				if (address != 0) {
					@SuppressWarnings("deprecation")
					final String ipAddress = Formatter.formatIpAddress(address);
					addressText.setText(ipAddress);
				} else {
					addressText.setText(R.string.wifiNotConnected);
				}
			}
		});
	}
	
}
