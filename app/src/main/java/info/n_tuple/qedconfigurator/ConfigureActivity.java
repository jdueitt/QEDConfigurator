package info.n_tuple.qedconfigurator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public class ConfigureActivity extends AppCompatActivity {
    private TextView _configureText;
    private Button _configureButton;
    private Handler _handler = new Handler();

    private static final UUID SerialPortServiceClass_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter _adapter;
    private BluetoothDevice _device;
    private BluetoothSocket _socket;
    private InputStream _inputStream;
    private InputStreamReader _inputStreamReader;
    private BufferedReader _bufferedReader;
    private OutputStream _outputStream;

    private States _state;


    enum States {
        STATE_CONNECTED, /* Indicates that a socket connection to the RN42 has been made */
        STATE_RN42_CMD_9600,
        STATE_NEO7_BAUD,
        STATE_RN42_CMD_38400,
        STATE_NEO7_SAVE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure);

        _configureText = (TextView) findViewById(R.id.configureText);
        _configureButton = (Button) findViewById(R.id.configureButton);

        _configureText.append("Starting configurator...\n");

        _adapter = BluetoothAdapter.getDefaultAdapter();

        _handler.postDelayed(connectRunnable, 2000);
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

                        _handler.postDelayed(configureRunnable, 1000);
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
            byte packet[] = null;
            switch (_state) {
                case STATE_CONNECTED:
                    /* Create the input/output streams */
                    try {
                        _inputStream = _socket.getInputStream();
                        _inputStreamReader = new InputStreamReader(_inputStream);
                        _outputStream = _socket.getOutputStream();
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException in creating streams: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Successfully setup input and output streams.\n");
                    _state = States.STATE_RN42_CMD_9600;
                    _handler.postDelayed(configureRunnable, 1000);
                    break;
                case STATE_RN42_CMD_9600:
                    command = "$$$\r";
                    try {
                        _outputStream.write(command.getBytes());
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException in entering command mode on RN42: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent $$$ to enter command mode on RN42\n");
                    command = "U,9600,N\r";
                    try {
                        _outputStream.write(command.getBytes());
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException setting temporary baud rate to 9600,N on RN42: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent U,9600,N to set temporary baud rate on RN42\n");

                    _state = States.STATE_NEO7_BAUD;
                    _handler.postDelayed(configureRunnable, 1000);
                    break;
                case STATE_NEO7_BAUD:
                    packet = new byte[] {
                            (byte)0xB5, 0x62, 0x06, 0x00, 0x14, 0x00, 0x01, 0x00, 0x00, 0x00,
                            (byte)0xD0, 0x08, 0x00, 0x00, 0x00, (byte)0x96, 0x00, 0x00, 0x07, 0x00,
                            0x03, 0x00, 0x00, 0x00, 0x00, 0x00, (byte)0x93, (byte)0x90};
                    try {
                        _outputStream.write(packet);
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException sending packet to configure UART1 baud rate to 38400: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent packet to set UART1 baud rate on NEO7P to 38400\n");
                    _state = States.STATE_RN42_CMD_38400;
                    _handler.postDelayed(configureRunnable, 1000);
                    break;
                case STATE_RN42_CMD_38400:
                    command = "$$$\r";
                    try {
                        _outputStream.write(command.getBytes());
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException in entering command mode on RN42: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent $$$ to enter command mode on RN42\n");
                    command = "U,38.4K,N\r";
                    try {
                        _outputStream.write(command.getBytes());
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException setting temporary baud rate to 38.4,N on RN42: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent U,38.4,N to set temporary baud rate on RN42\n");
                    command = "SU,38400\r";
                    try {
                        _outputStream.write(command.getBytes());
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException setting permanent baud rate to 38.4K on RN42: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent SU,38400 to set permanent baud rate on RN42\n");
                    command = "---";
                    try {
                        _outputStream.write(command.getBytes());
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException leaving command mode on RN42: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent --- to exit command mode on RN42\n");
                    _state = States.STATE_NEO7_SAVE;
                    _handler.postDelayed(configureRunnable, 1000);
                    break;
                case STATE_NEO7_SAVE:
                    packet = new byte[] {
                            (byte)0xB5, 0x62, 0x06, 0x09, 0x0D, 0x00, 0x00, 0x00, 0x00, 0x00,
                            (byte)0xFF, (byte)0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x1D,
                            (byte)0xAB};
                    try {
                        _outputStream.write(packet);
                    } catch (IOException e) {
                        String message = new String();
                        message.format("Caught IOException sending packet to save NEO7 configuration: %s\n", e.getMessage());
                        _configureText.append(message);

                        _inputStreamReader = null;
                        _inputStream = null;
                        _outputStream = null;
                        _socket = null;
                        _device = null;
                        _adapter = null;

                        return;
                    }
                    _configureText.append("Sent packet to save configuration to BBR and FLASH on NEO7P\n");

                    _inputStreamReader = null;
                    _inputStream = null;
                    _outputStream = null;
                    _socket = null;
                    _device = null;
                    _adapter = null;

                    break;
            }
        }
    };
}
