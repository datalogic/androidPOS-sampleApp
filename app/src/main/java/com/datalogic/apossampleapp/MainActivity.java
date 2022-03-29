package com.datalogic.apossampleapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.datalogic.apossampleapp.databinding.ActivityMainBinding;
import com.datalogic.apossampleapp.helpers.UriHelper;
import com.datalogic.dlapos.androidpos.common.DLSJposConst;
import com.datalogic.dlapos.commons.constant.CommonsConstants;
import com.datalogic.dlapos.commons.support.APosException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    private ExampleViewModel viewModel;
    private static final int READ_STORAGE_PERMISSION_REQUEST_CODE = 41;
    private static final int CREATE_FILE = 1;
    private static final int PICK_FILE = 2;
    private String logicalName = "DL-Gryphon-GD4500-USB-OEM";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        viewModel = new ViewModelProvider(this).get(ExampleViewModel.class);

        if (!checkPermissionForReadExternalStorage()) {
            try {
                requestPermissionForReadExternalStorage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // handle profile spinner selections
        binding.logicalNameSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                logicalName = (String) adapterView.getItemAtPosition(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //nothing to do here
            }
        });

        binding.closeButton.setOnClickListener(view1 -> viewModel.close());
        binding.openButton.setOnClickListener(view1 -> viewModel.open(logicalName, getApplicationContext()));
        binding.claimButton.setOnClickListener(view1 -> viewModel.claim());
        binding.releaseButton.setOnClickListener(view1 -> viewModel.release());
        binding.enableButton.setOnClickListener(view1 -> viewModel.enable());
        binding.disableButton.setOnClickListener(view1 -> viewModel.disable());
        binding.readWeightButton.setOnClickListener(view1 -> viewModel.readWeight());
        binding.saveStatistics.setOnClickListener(view1 -> viewModel.retrieveStatistics());
        binding.openFirmwareButton.setOnClickListener(view1 -> pickFile());
        binding.upgradeButton.setOnClickListener(view1 -> viewModel.updateFirmware());
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.statistics_file_types,
                android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerStatistics.setAdapter(spinnerAdapter);
        binding.spinnerStatistics.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0)
                    viewModel.setStatisticsFormat(ExampleViewModel.StatisticsFormat.XML);
                else if (position == 1)
                    viewModel.setStatisticsFormat(ExampleViewModel.StatisticsFormat.AVALANCHE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        binding.asyncSwitch.setOnCheckedChangeListener((compoundButton, checked) -> {
            viewModel.setScaleAsync(checked);
        });

        viewModel.getStatus().observe(this, status -> {
            handleButtonsOnStatusChange(status);
            switch (status) {
                case OPENED:
                    try {
                        binding.productNameValue.setText(viewModel.getProductName());
                        binding.additionalDataValue.setText(viewModel.getAdditionalData());
                    } catch (APosException e) {
                        e.printStackTrace();
                    }
                    break;
                case ENABLED:
                    try {
                        binding.additionalDataValue.setText(viewModel.getAdditionalData());
                    } catch (APosException e) {
                        e.printStackTrace();
                    }
                    break;
                case ERROR:
                    binding.additionalDataValue.setText(viewModel.getErrorMessage());
                default:
                    break;
            }
            binding.statusValue.setText(status.name());
        });
        viewModel.getBarcodeContent().observe(this, content -> binding.barcodeContentValue.setText(content));
        viewModel.getBarcodeSymbology().observe(this, symbology -> binding.barcodeSymbologyValue.setText(symbology));
        viewModel.getConnectionStatus().observe(this, this::handleConnectionStatusChange);
        viewModel.getStatistics().observe(this, stats -> createFile());
        viewModel.isUpgradable().observe(this, upgradable -> binding.upgradeButton.setEnabled(upgradable));
        viewModel.getDeviceFirmwareVersion().observe(this, this::handleFirmwareComparisonResult);
        viewModel.getWeight().observe(this, weight -> binding.weightValue.setText(weight + " " + viewModel.getWeightUnit()));

        // create adapter based on profiles contained in apos.json and populate profile spinner
        viewModel.getProfiles(this).observe(this, profiles -> {
            ArrayAdapter<String> newAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, profiles);
            newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.logicalNameSpinner.setAdapter(newAdapter);
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == CREATE_FILE) {
            try {
                InputStream is = new ByteArrayInputStream(viewModel.getStatistics().getValue().getBytes());
                OutputStream os = getContentResolver().openOutputStream(data.getData());
                byte[] buf = new byte[8192];
                int length;
                while ((length = is.read(buf)) > 0) {
                    os.write(buf, 0, length);
                }
                os.flush();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (resultCode == RESULT_OK && requestCode == PICK_FILE) {
            String filePath = UriHelper.getPath(getApplicationContext(), data.getData());
            viewModel.compareFirmware(filePath);
        }
    }

    protected void createFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String format = (viewModel.getStatisticFormat() == ExampleViewModel.StatisticsFormat.XML) ? "xml" : "txt";
        intent.setType("text/" + format);
        intent.putExtra(Intent.EXTRA_TITLE, logicalName + "_stats." + format);

        startActivityForResult(intent, CREATE_FILE);
    }

    protected void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/octet-stream");
        startActivityForResult(intent, PICK_FILE);
    }

    private void handleConnectionStatusChange(int status) {
        if (status == CommonsConstants.SUE_POWER_ONLINE)
            binding.deviceConnectionState.setImageResource(android.R.drawable.presence_online);
        else if (status == CommonsConstants.SUE_POWER_OFF || status == CommonsConstants.SUE_POWER_OFF_OFFLINE || status == CommonsConstants.SUE_POWER_OFFLINE || status == CommonsConstants.PS_UNKNOWN)
            binding.deviceConnectionState.setImageResource(android.R.drawable.presence_offline);
        else if (status >= CommonsConstants.SUE_UF_PROGRESS && status <= CommonsConstants.SUE_UF_COMPLETE) {
            if (status == CommonsConstants.SUE_UF_PROGRESS) {
                disableAll();
                prepareFirmwareComparisonLabels();
            }
            if (status < CommonsConstants.SUE_UF_COMPLETE) {
                binding.firmwareProgress.setProgress(status - CommonsConstants.SUE_UF_PROGRESS);
                updatePercentage(status - CommonsConstants.SUE_UF_PROGRESS);
            } else
                resetUpdateData();
        }
    }

    private void disableAll() {
        binding.openButton.setEnabled(false);
        binding.closeButton.setEnabled(false);
        binding.claimButton.setEnabled(false);
        binding.releaseButton.setEnabled(false);
        binding.enableButton.setEnabled(false);
        binding.disableButton.setEnabled(false);
        binding.logicalNameSpinner.setEnabled(false);
        binding.openFirmwareButton.setEnabled(false);
        binding.upgradeButton.setEnabled(false);
        binding.saveStatistics.setEnabled(false);
    }

    private void prepareFirmwareComparisonLabels() {
        binding.firmwareComparisonResultLabel.setText("Upgrading: ");
    }

    private void updatePercentage(int percentage) {
        binding.firmwareComparisonValue.setText(MessageFormat.format("{0}%", percentage));
    }

    private void resetUpdateData() {
        binding.currentVersionValue.setText(R.string.na);
        binding.firmwareComparisonValue.setText(R.string.na);
        binding.firmwareComparisonResultLabel.setText(R.string.comparison);
        binding.fileVersionValue.setText(R.string.na);
        binding.firmwareProgress.setProgress(0);
        handleButtonsOnStatusChange(viewModel.getStatus().getValue());
    }

    private void handleButtonsOnStatusChange(ExampleViewModel.Status status) {
        if (status == ExampleViewModel.Status.ERROR)
            return;

        binding.openButton.setEnabled(status == ExampleViewModel.Status.CLOSED);
        binding.closeButton.setEnabled(status == ExampleViewModel.Status.OPENED);
        binding.claimButton.setEnabled(status == ExampleViewModel.Status.OPENED);
        binding.releaseButton.setEnabled(status == ExampleViewModel.Status.CLAIMED);
        binding.enableButton.setEnabled(status == ExampleViewModel.Status.CLAIMED);
        binding.disableButton.setEnabled(status == ExampleViewModel.Status.ENABLED);
        binding.logicalNameSpinner.setEnabled(status == ExampleViewModel.Status.CLOSED);
        binding.openFirmwareButton.setEnabled(status == ExampleViewModel.Status.ENABLED);
        binding.saveStatistics.setEnabled(status == ExampleViewModel.Status.ENABLED);
        binding.readWeightButton.setEnabled(viewModel.isDeviceAScale() && status == ExampleViewModel.Status.ENABLED);
        binding.asyncSwitch.setEnabled(viewModel.isDeviceAScale() && status == ExampleViewModel.Status.ENABLED);
        if (!viewModel.isDeviceAScale() || status != ExampleViewModel.Status.ENABLED)
            binding.asyncSwitch.setChecked(false);
    }

    private void handleFirmwareComparisonResult(int[] result) {
        String comparisonStatus = "N/A";
        String deviceVersion = "" + binding.currentVersionValue.getText();
        String fileVersion = "" + binding.fileVersionValue.getText();
        boolean enableUpgradeButton = false;
        switch (result[0]) {
            case -1:
                comparisonStatus = viewModel.getErrorMessage();
                fileVersion = "N/A";
                break;
            case CommonsConstants.CVF_FIRMWARE_OLDER:
                comparisonStatus = "The installed version is newer.";
                fileVersion = "" + result[3];
                deviceVersion = "" + result[4];
                enableUpgradeButton = true;
                break;
            case CommonsConstants.CVF_FIRMWARE_NEWER:
                comparisonStatus = "The installed version is older.";
                fileVersion = "" + result[3];
                deviceVersion = "" + result[4];
                enableUpgradeButton = true;
                break;
            case CommonsConstants.CVF_FIRMWARE_SAME:
                comparisonStatus = "The installed version is the same.";
                fileVersion = "" + result[3];
                deviceVersion = "" + result[4];
                enableUpgradeButton = true;
                break;
            case CommonsConstants.CVF_FIRMWARE_UNKNOWN:
                comparisonStatus = "Can not get the device firmware.";
                fileVersion = "N/A";
                deviceVersion = "N/A";
                break;
            case DLSJposConst.DLS_CFV_INVALID_VID:
                comparisonStatus = "Invalid vendor " + result[2] + ".";
                fileVersion = "N/A";
                break;
            case DLSJposConst.DLS_CFV_INVALID_PID:
                comparisonStatus = "Invalid product " + result[2] + ".";
                fileVersion = "N/A";
        }
        binding.firmwareComparisonValue.setText(comparisonStatus);
        binding.currentVersionValue.setText(deviceVersion);
        binding.fileVersionValue.setText(fileVersion);
        binding.upgradeButton.setEnabled(enableUpgradeButton);
    }

    @Override
    protected void onDestroy() {
        viewModel.cleanDatabase(this);
        super.onDestroy();
    }

    public boolean checkPermissionForReadExternalStorage() {
        int result = getApplicationContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermissionForReadExternalStorage() throws Exception {
        try {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    READ_STORAGE_PERMISSION_REQUEST_CODE);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}