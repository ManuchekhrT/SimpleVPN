package com.java.simplevpn.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.java.simplevpn.utils.CheckInternetConnection;
import com.java.simplevpn.R;
import com.java.simplevpn.databinding.FragmentMainBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.blinkt.openvpn.OpenVpnApi;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.OpenVPNThread;
import de.blinkt.openvpn.core.VpnStatus;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class MainFragment extends Fragment implements View.OnClickListener {

    private CheckInternetConnection connection;

    private OpenVPNThread vpnThread = new OpenVPNThread();
    private OpenVPNService vpnService = new OpenVPNService();
    boolean vpnStart = false;
    private FragmentMainBinding binding;


    public MainFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false);
        View view = binding.getRoot();

        connection = new CheckInternetConnection();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnActionVpn.setOnClickListener(this);

        isServiceRunning();
        VpnStatus.initLogCache(getActivity().getCacheDir());
    }


    /**
     * Internet connection status.
     */
    public boolean getInternetStatus() {
        return connection.netCheck(requireContext());
    }

    /**
     * Get service status
     */
    public void isServiceRunning() {
        setStatus(vpnService.getStatus());
    }

    /**
     * Status change with corresponding vpn connection status
     *
     * @param connectionState
     */
    @SuppressLint("SetTextI18n")
    public void setStatus(String connectionState) {
        if (connectionState != null) {
            switch (connectionState) {
                case "DISCONNECTED":
                    status(StatusState.connect.name());
                    vpnStart = false;
                    vpnService.setDefaultStatus();
                    binding.tvLog.setText("");
                    break;
                case "CONNECTED":
                    vpnStart = true;// it will use after restart this activity
                    status(StatusState.connected.name());
                    binding.tvLog.setText("");
                    break;
                case "WAIT":
                    binding.tvLog.setVisibility(View.VISIBLE);
                    binding.tvLog.setText(requireContext().getString(R.string.server_conn));
                    break;
                case "AUTH":
                    binding.tvLog.setVisibility(View.VISIBLE);
                    binding.tvLog.setText(requireContext().getString(R.string.server_auth));
                    break;
                case "RECONNECTING":
                    status(StatusState.connecting.name());
                    binding.tvLog.setVisibility(View.VISIBLE);
                    binding.tvLog.setText(requireContext().getString(R.string.reconnecting));
                    break;
                case "NONETWORK":
                    binding.tvLog.setVisibility(View.VISIBLE);
                    binding.tvLog.setText(requireContext().getString(R.string.no_network_conn));
                    break;

            }
        }

    }


    /**
     * Change button background color and text
     *
     * @param status: VPN current status
     */
    public void status(String status) {
        if (status.equals(StatusState.connect.name())) {
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.bg_vpn_off_btn, null);
            binding.btnActionVpn.setBackground(drawable);
            binding.btnActionVpn.setText(requireContext().getString(R.string.vpn_connect));
        } else if (status.equals(StatusState.connecting.name())) {
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.bg_btn_vpn_loading, null);
            binding.btnActionVpn.setBackground(drawable);
            binding.btnActionVpn.setText(requireContext().getString(R.string.connecting));
        } else if (status.equals(StatusState.connected.name())) {
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.bg_btn_vpn_on, null);
            binding.btnActionVpn.setBackground(drawable);
            binding.btnActionVpn.setText(requireContext().getString(R.string.vpn_disconnect));
        } else if (status.equals(StatusState.tryDifferentServer.name())) {
            binding.btnActionVpn.setText(requireContext().getString(R.string.try_different_server));
        } else if (status.equals(StatusState.loading.name())) {
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.bg_btn_vpn_loading, null);
            binding.btnActionVpn.setBackground(drawable);
            binding.btnActionVpn.setText(requireContext().getString(R.string.server_loading));
        } else if (status.equals(StatusState.invalidDevice.name())) {
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.bg_btn_vpn_loading, null);
            binding.btnActionVpn.setBackground(drawable);
            binding.btnActionVpn.setText(requireContext().getString(R.string.invalid_device));
        } else if (status.equals(StatusState.authenticationCheck.name())) {
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.bg_btn_vpn_loading, null);
            binding.btnActionVpn.setBackground(drawable);
            binding.btnActionVpn.setText(requireContext().getString(R.string.auth_checking));
        }

    }

    enum StatusState {
        connect, connecting, connected, tryDifferentServer, loading, invalidDevice, authenticationCheck
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_action_vpn) {// Vpn is running, user would like to disconnect current connection.
            if (vpnStart) {
                confirmDisconnect();
            } else {
                prepareVpn();
            }
        }
    }

    private void confirmDisconnect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(requireActivity().getString(R.string.connection_close_confirm));

        builder.setPositiveButton(requireActivity().getString(R.string.yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                stopVpn();
            }
        });
        builder.setNegativeButton(requireActivity().getString(R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });

        // Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void prepareVpn() {
        if (!vpnStart) {
            if (getInternetStatus()) {

                // Checking permission for network monitor
                Intent intent = VpnService.prepare(getContext());

                if (intent != null) {
                    startActivityForResult(intent, 1);
                } else startVpn();//have already permission

                status(StatusState.connecting.name());

            } else {
                // No internet connection available
                showToast(requireContext().getString(R.string.no_internet_conn));
            }

        } else if (stopVpn()) {
            // VPN is stopped, show a Toast message.
            showToast(requireContext().getString(R.string.vpn_off_success));
        }
    }

    /**
     * Start the VPN
     */
    private void startVpn() {
        try {
            // .ovpn file
            InputStream conf = getActivity().getAssets().open("netherlands.ovpn");
            InputStreamReader isr = new InputStreamReader(conf);
            BufferedReader br = new BufferedReader(isr);
            String config = "";
            String line;

            while (true) {
                line = br.readLine();
                if (line == null) break;
                config += line + "\n";
            }

            br.readLine();
            OpenVpnApi.startVpn(getContext(), config, requireContext().getString(R.string.cc_netherlands), requireContext().getString(R.string.cc_vpn), requireContext().getString(R.string.cc_vpn));

            // Update log
            binding.tvLog.setText(requireContext().getString(R.string.connecting));
            vpnStart = true;

        } catch (IOException | RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop vpn
     *
     * @return boolean: VPN status
     */
    public boolean stopVpn() {
        try {
            vpnThread.stop();

            status(StatusState.connect.name());
            vpnStart = false;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Show toast message
     *
     * @param message: toast message
     */
    public void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }


    /**
     * Taking permission for network access
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {

            //Permission granted, start the VPN
            startVpn();
        } else {
            showToast(requireContext().getString(R.string.permission_denied));
        }
    }


    /**
     * Receive broadcast message
     */
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                setStatus(intent.getStringExtra("state"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onResume() {
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(broadcastReceiver, new IntentFilter("connectionState"));
        super.onResume();
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

}