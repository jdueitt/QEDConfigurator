package info.n_tuple.qedconfigurator;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.util.St

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
    private int _state;

    private final int STATE_CONNECTED = 0;
    private final int STATE_CONFIG_UBLOX_PORT = 1;
    private final int STATE_CONFIG_BT_CMD = 2;
    private final int STATE_CONFIG_BT_BAUD = 3;
    private final int STATE_CONFIG_BT_DATA = 4;
    private final int STATE_CONFIG_UBLOX_SAVE = 5;
    private final int STATE_CONFIG_DONE = 6;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure);

        _configureText = (TextView)findViewById(R.id.configureText);
        _configureButton = (Button)findViewById(R.id.configureButton);

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
            }
            else if (_adapter.isEnabled()) {
                _configureText.append("Bluetooth enabled, checking for paired devices...\n");
                Set<BluetoothDevice> pairedDevices = _adapter.getBondedDevices();
                if (pairedDevices.size() > 0) {
                    String message = new String();
                    message.format("Found %d paired devices. Using the first device.\n", pairedDevices.size());
                    _configureText.append(message);
                    _device = pairedDevices.iterator().next();
                    message.format("Using device %s for configuration.\n", _device.getName());
                    _configureText.append(message);
                    try {
                        _adapter.cancelDiscovery();
                        _socket = _device.createRfcommSocketToServiceRecord(SerialPortServiceClass_UUID);
                        _socket.connect();
                        _state = STATE_CONNECTED;
                        _handler.postDelayed(configureRunnable, 1000);
                    }
                    catch (IOException e)
                    {
                        message.format("Caught IOException in creating RFCOMM socket. Retrying in 5 seconds.\n");
                        _device = null;
                        _socket = null;
                        _adapter.startDiscovery();
                        _handler.postDelayed(connectRunnable, 5000);
                    }
                }
                else
                {
                    _configureText.append("No bluetooth devices found. Retrying in 5 seconds.\n");
                    _handler.postDelayed(connectRunnable, 5000);
                }
            }
        }
    };

    private Runnable configureRunnable = new Runnable() {
        @Override
        public void run() {

        }
    };
}
