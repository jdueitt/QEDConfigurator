package info.n_tuple.qedconfigurator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public class ConfigureActivity extends AppCompatActivity {
    private TextView _configureText;
    private TextView _resultText;
    private Button _configureButton;
    private Handler _handler = new Handler();

    private static final UUID SerialPortServiceClass_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter _adapter;
    private BluetoothDevice _device;
    private BluetoothSocket _socket;
    private InputStream _inputStream;
    private InputStreamReader _inputStreamReader;
    private OutputStream _outputStream;

    private States _state;

    private List<byte[]> _packets = new LinkedList<byte[]>();
    private List<String> _packetDescriptions = new LinkedList<String>();

    private Thread _readerThread;


    enum States {
        STATE_CONNECTED, /* Indicates that a socket connection to the RN42 has been made */
        STATE_RN42_CMD_9600,
        STATE_NEO7_BAUD,
        STATE_RN42_CMD_38400,
        STATE_NEO7_SAVE,
        STATE_DONE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure);

        _configureText = (TextView) findViewById(R.id.configureText);
        _resultText = (TextView) findViewById(R.id.resultText);
        _configureButton = (Button) findViewById(R.id.configureButton);

        _resultText.setMovementMethod(new ScrollingMovementMethod());
        _configureText.setMovementMethod(new ScrollingMovementMethod());
    }

    public void startConfiguration(View view) {
        _adapter = BluetoothAdapter.getDefaultAdapter();

        _configureText.append("Starting configurator...\n");
        _handler.postDelayed(connectRunnable, 100);
    }

    public class ReaderThread implements Runnable {
        @Override
        public void run() {
            int read = -1;
            byte[] buffer = new byte[128];

            while (true)
            {
                try
                {
                    read = _inputStream.read(buffer, 0, 1);
                } catch (IOException e) {
                    break;
                }

                if (read > 0) {
                    final String text = new String(buffer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            _resultText.append(text);
                        }
                    });
                }

            }
        }
    }

    private void readStream() {
        byte[] buffer = new byte[256];
        int read = 0;

        try {
            if (_inputStream.available() <= 0)
                return;

            read = _inputStream.read(buffer, 0, 256);
        } catch (IOException e) {
            return;
        }

        while (read > 0)
        {
            String input = new String(buffer);
            _resultText.append(input);

            try {
                if (_inputStream.available() <= 0)
                    break;

                read = _inputStream.read(buffer, 0, 256);
            } catch (IOException e) {
                return;
            }
        }
    }

    private void closeSocketsAndStreams() {
        try {
            //_inputStreamReader.close();
            //_inputStreamReader = null;

            _inputStream.close();
            _inputStream = null;

            _outputStream.close();
            _outputStream = null;
        } catch (IOException e) {
            String message = new String();
            message.format("IOException closing sockets and streams: %s\n", e.getMessage());
            _configureText.append(message);
        }
    }

    private Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            if (_adapter == null) {
                _configureText.append("No Bluetooth adapter found, aborting.\n");
                return;
            } else if (!_adapter.isEnabled()) {
                _configureText.append("Bluetooth adapter found, but it is disabled. Aborting.\n");
                return;
            } else if (_adapter.isEnabled()) {
                _configureText.append("Bluetooth enabled, checking for paired devices...\n");

                _adapter.startDiscovery();

                Set<BluetoothDevice> pairedDevices = _adapter.getBondedDevices();

                if (pairedDevices.size() > 0) {
                    String message = new String();
                    message.format("Found %d paired devices. Using the first device.\n", pairedDevices.size());
                    _configureText.append(message);

                    _device = pairedDevices.iterator().next();

                    message.format("Using device %s for configuration.\n", _device.getName());
                    _configureText.append(message);

                    try {
                        _socket = _device.createRfcommSocketToServiceRecord(SerialPortServiceClass_UUID);

                        _adapter.cancelDiscovery();

                        _socket.connect();

                        _state = States.STATE_CONNECTED;

                        _handler.post(configureRunnable);
                    } catch (IOException e) {
                        message.format("Caught IOException in creating RFCOMM socket: %s. Retrying in 5 seconds.\n", e.getMessage());
                        _configureText.append(message);

                        _device = null;
                        _socket = null;
                        _adapter.startDiscovery();

                        _handler.postDelayed(connectRunnable, 5000);
                    }
                } else {
                    _configureText.append("No paired bluetooth devices found. Retrying in 5 seconds.\n");

                    _handler.postDelayed(connectRunnable, 5000);
                }
            }
        }
    };

    private Runnable configureRunnable = new Runnable() {
        @Override
        public void run() {
            String command = new String();
            String description = null;
            byte packet[] = null;

            switch (_state) {
                case STATE_CONNECTED:
                    /* Create the input/output streams */
                    try {
                        _inputStream = _socket.getInputStream();
                        //_inputStreamReader = new InputStreamReader(_inputStream);
                        _outputStream = _socket.getOutputStream();
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException in creating streams: %s\n", e.getMessage());
                        _configureText.append(message);

                        closeSocketsAndStreams();

                        return;
                    }
                    _configureText.append("Successfully setup input and output streams.\n");

                    //_readerThread = new Thread(new ReaderThread());
                    //_readerThread.start();

                    /* Setup the packets and packet descriptions */
                    _packets.add("$$$".getBytes());
                    _packetDescriptions.add("Entering command mode on RN42");
                    _packets.add("U,9600,N\r".getBytes());
                    _packetDescriptions.add("Setting temporary baud rate to 9600,N on RN42");

                    _state = States.STATE_RN42_CMD_9600;
                    _handler.post(configureRunnable);
                    break;

                case STATE_RN42_CMD_9600:
                    readStream();

                    packet = _packets.get(0);
                    description = _packetDescriptions.get(0);

                    _packets.remove(0);
                    _packetDescriptions.remove(0);

                    try {
                        _outputStream.write(packet);
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException while\n\t%s:\n\t%s\n", description, e.getMessage());
                        _configureText.append(message);

                        closeSocketsAndStreams();

                        return;
                    }
                    _configureText.append(description + "\n");

                    if (_packets.size() == 0) {
                        _packets.add(new byte[]{
                                (byte) 0xB5, 0x62, 0x06, 0x00, 0x14, 0x00, 0x01, 0x00, 0x00, 0x00,
                                (byte) 0xD0, 0x08, 0x00, 0x00, 0x00, (byte) 0x96, 0x00, 0x00, 0x07, 0x00,
                                0x03, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x93, (byte) 0x90});
                        _packetDescriptions.add("Configuring NEO7P baud rate to 38400bps");

                        _state = States.STATE_NEO7_BAUD;
                        //_state = States.STATE_DONE;
                        _handler.postDelayed(configureRunnable, 2000);
                        //_handler.post(configureRunnable);
                    } else {
                        _handler.postDelayed(configureRunnable, 2000);
                    }
                    break;

                case STATE_NEO7_BAUD:
                    readStream();

                    packet = _packets.get(0);
                    description = _packetDescriptions.get(0);

                    _packets.remove(0);
                    _packetDescriptions.remove(0);

                    try {
                        _outputStream.write(packet);
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException while\n\t%s:\n\t%s\n", description, e.getMessage());
                        _configureText.append(message);

                        closeSocketsAndStreams();

                        return;
                    }
                    _configureText.append(description + "\n");

                    if (_packets.size() == 0) {
                        _packets.add("$$$".getBytes());
                        _packetDescriptions.add("Entering command mode on RN42");

                        _packets.add("SU,38400\r".getBytes());
                        _packetDescriptions.add("Setting permanent baud rate to 38,400bps");

                        _packets.add("U,38.4,N\r".getBytes());
                        _packetDescriptions.add("Setting the temporary baud rate to 38,400bps");

                        _state = States.STATE_RN42_CMD_38400;

                        _handler.postDelayed(configureRunnable, 5000);
                    } else {
                        _handler.postDelayed(configureRunnable, 2000);
                    }
                    break;

                case STATE_RN42_CMD_38400:
                    readStream();

                    packet = _packets.get(0);
                    description = _packetDescriptions.get(0);

                    _packets.remove(0);
                    _packetDescriptions.remove(0);

                    try {
                        _outputStream.write(packet);
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException while\n\t%s:\n\t%s\n", description, e.getMessage());
                        _configureText.append(message);

                        closeSocketsAndStreams();

                        return;
                    }
                    _configureText.append(description + "\n");

                    if (_packets.size() == 0) {
                        _packets.add(new byte[]{
                                (byte) 0xB5, 0x62, 0x06, 0x09, 0x0D, 0x00, 0x00, 0x00, 0x00, 0x00,
                                (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x1D,
                                (byte) 0xAB});
                        _packetDescriptions.add("Saving NEO7P configuration to BBR and Flash.");

                        _state = States.STATE_NEO7_SAVE;
                        _handler.postDelayed(configureRunnable, 5000);
                    } else {
                        _handler.postDelayed(configureRunnable, 2000);
                    }
                    break;

                case STATE_NEO7_SAVE:
                    readStream();

                    packet = _packets.get(0);
                    description = _packetDescriptions.get(0);

                    _packets.remove(0);
                    _packetDescriptions.remove(0);

                    try {
                        _outputStream.write(packet);
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException while\n\t%s:\n\t%s\n", description, e.getMessage());
                        _configureText.append(message);

                        closeSocketsAndStreams();

                        return;
                    }
                    _configureText.append(description + "\n");

                    if (_packets.size() == 0) {
                        closeSocketsAndStreams();

                        _configureText.append("Done!");
                    } else {
                        _handler.postDelayed(configureRunnable, 2000);
                    }

                    break;
                case STATE_DONE:
                    readStream();
                    _handler.postDelayed(configureRunnable, 500);
                    break;
            }
        }
    };
}
