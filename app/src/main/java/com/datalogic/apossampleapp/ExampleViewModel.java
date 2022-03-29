package com.datalogic.apossampleapp;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.datalogic.dlapos.commons.constant.CommonsConstants;
import com.datalogic.dlapos.commons.constant.ScaleConstants;
import com.datalogic.dlapos.commons.control.BaseControl;
import com.datalogic.dlapos.commons.event.BaseEvent;
import com.datalogic.dlapos.commons.event.DataEvent;
import com.datalogic.dlapos.commons.event.ErrorEvent;
import com.datalogic.dlapos.commons.event.EventCallback;
import com.datalogic.dlapos.commons.event.StatusUpdateEvent;
import com.datalogic.dlapos.commons.support.APosException;
import com.datalogic.dlapos.commons.upos.RequestListener;
import com.datalogic.dlapos.confighelper.DLAPosConfigHelper;
import com.datalogic.dlapos.control.Scale;
import com.datalogic.dlapos.control.Scanner;

import java.nio.charset.StandardCharsets;
import java.util.EventListener;
import java.util.List;


public class ExampleViewModel extends ViewModel implements EventListener, com.datalogic.dlapos.control.event.EventListener {
    public enum Status {
        CLOSED,
        OPENED,
        CLAIMED,
        ENABLED,
        ERROR
    }

    public enum StatisticsFormat {
        XML,
        AVALANCHE
    }

    private String _logicalName;
    private MutableLiveData<Boolean> _online;
    private MutableLiveData<Status> _status;
    private MutableLiveData<String> _barcodeContent;
    private MutableLiveData<String> _barcodeSymbology;
    private MutableLiveData<List<String>> _profileIDs;
    private MutableLiveData<Integer> _connectionStatus;
    private MutableLiveData<String> _statistics;
    private MutableLiveData<Boolean> _isUpgradable;
    private MutableLiveData<int[]> _firmwareInfo;
    private MutableLiveData<Double> _weight;
    private String _errorMessage = "";
    private BaseControl _device;
    private StatisticsFormat statisticsFormat = StatisticsFormat.XML;
    private String _filePath;

    @Override
    public void onEvent(BaseEvent event) {
        try {
            if (event instanceof DataEvent) {
                if (_device instanceof Scanner) {
                    _barcodeContent.postValue(new String(((Scanner) _device).getScanDataLabel(), StandardCharsets.UTF_8));
                    _barcodeSymbology.postValue("" + ((Scanner) _device).getScanDataType());
                    _device.setDataEventEnabled(true);
                } else {
                    _weight.postValue(formatWeight(((DataEvent) event).getStatus()));
                }
            }
            if (event instanceof StatusUpdateEvent) {
                _connectionStatus.postValue(((StatusUpdateEvent) event).getStatus());
            }
            if (!(event instanceof ErrorEvent)) {
                _status.postValue(Status.ENABLED);
            }
        } catch (APosException e) {
            Log.e("Example", "Receiving event: ", e);
            _errorMessage = e.getMessage();
            _status.postValue(Status.ERROR);
        }
    }


    public void open(String logicalName, Context context) {
        _logicalName = logicalName;
        new Thread(() -> {
            try {
                if (logicalName.contains("Scale") && !logicalName.contains("Scanner"))
                    _device = new Scale();
                else
                    _device = new Scanner();
                _device.open(logicalName, context);
                _status.postValue(Status.OPENED);
            } catch (APosException e) {
                Log.e("Example", "Opening: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }).start();
    }

    public void claim() {
        new Thread(() -> {
            try {
                _device.claim(new RequestListener() {
                    @Override
                    public void onSuccess() {
                        _errorMessage = "N/A";
                        _status.postValue(Status.CLAIMED);
                    }

                    @Override
                    public void onFailure(String failureDescription) {
                        _errorMessage = failureDescription;
                        _status.postValue(Status.ERROR);
                    }
                });
            } catch (APosException e) {
                Log.e("Example", "Claiming: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }
        ).start();
    }

    public void retrieveStatistics() {
        new Thread(() -> {
            try {
                String[] stats = new String[2];
                stats[0] = "";
                _device.retrieveStatistics(stats);
                if (statisticsFormat == StatisticsFormat.XML)
                    _statistics.postValue(stats[0]);
                else
                    _statistics.postValue(stats[1]);
            } catch (APosException e) {
                Log.e("Example", "SavingStatistics: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }).start();
    }

    public void readWeight() {
        if (_device instanceof Scale) {
            int[] result = new int[3];
            try {
                if (((Scale) _device).getAsyncMode())
                    _device.setDataEventEnabled(true);
                ((Scale) _device).readWeight(result, 1000);
                _weight.postValue(formatWeight(result[0]));
                _status.postValue(Status.ENABLED);
            } catch (APosException e) {
                Log.e("Example", "ReadingWeight: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }
    }

    private double formatWeight(int integerValue) {
        return (integerValue * 1d) / 1000d;
    }

    public void setScaleAsync(boolean asyncMode) {
        if (_device instanceof Scale) {
            try {
                ((Scale) _device).setAsyncMode(asyncMode);
            } catch (APosException e) {
                Log.e("Example", "Setting async mode: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        } else
            throw new UnsupportedOperationException("Only Scales have this functionality.");
    }

    public String getWeightUnit() {
        if (_device instanceof Scale) {
            try {
                int weightUnit = ((Scale) _device).getWeightUnit();
                String result = "";
                switch (weightUnit) {
                    case ScaleConstants.SCAL_WU_GRAM:
                        result = "grams";
                        break;
                    case ScaleConstants.SCAL_WU_KILOGRAM:
                        result = "kilograms";
                        break;
                    case ScaleConstants.SCAL_WU_OUNCE:
                        result = "ounces";
                        break;
                    case ScaleConstants.SCAL_WU_POUND:
                        result = "pounds";
                        break;
                }
                return result;
            } catch (APosException e) {
                Log.e("Example", "ReadingWeight: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }
        throw new UnsupportedOperationException("Only scales supports this function");
    }

    public void compareFirmware(String filePath) {
        new Thread(() -> {
            int[] result = new int[5];
            try {
                _device.compareFirmwareVersion(filePath, result);
                if (_firmwareInfo != null)
                    _firmwareInfo.postValue(result);
                this._filePath = filePath;
            } catch (APosException e) {
                Log.e("Example", "Comparing firmware: ", e);
                _errorMessage = e.getMessage();
                result[0] = -1;
                _firmwareInfo.postValue(result);
            }
        }).start();
    }

    public void updateFirmware() {
        new Thread(() -> {
            try {
                _device.updateFirmware(_filePath);
            } catch (APosException e) {
                Log.e("Example", "SavingStatistics: ", e);
                _errorMessage = e.getMessage();
            }
        }).start();
    }

    public void enable() {
        new Thread(() -> {
            try {
                if (_device instanceof Scanner) {
                    ((Scanner) _device).addEventListener(this, EventCallback.EventType.Data);
                    ((Scanner) _device).addEventListener(this, EventCallback.EventType.StatusUpdate);
                    _connectionStatus.postValue(_device.getPowerState());
                } else {
                    ((Scale) _device).addEventListener(this, EventCallback.EventType.Data);
                    ((Scale) _device).addEventListener(this, EventCallback.EventType.StatusUpdate);
                }
                _device.setAutoDisable(false);
                _status.postValue(Status.ENABLED);
                _device.setDataEventEnabled(true);
                _device.setPowerNotify(CommonsConstants.PN_ENABLED);
                _device.setDeviceEnabled(true);
            } catch (APosException e) {
                Log.e("Example", "Enabling: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }).start();
    }

    public void disable() {
        new Thread(() -> {
            try {
                if (_device instanceof Scanner) {
                    ((Scanner) _device).setDataEventEnabled(false);
                    ((Scanner) _device).setAutoDisable(true);
                    ((Scanner) _device).removeEventListener(this, EventCallback.EventType.Data);
                    ((Scanner) _device).setDeviceEnabled(false);
                    ((Scanner) _device).removeEventListener(this, EventCallback.EventType.StatusUpdate);
                } else {
                    _device.setAutoDisable(true);
                    _device.setDeviceEnabled(false);
                }
                _status.postValue(Status.CLAIMED);
            } catch (APosException e) {
                Log.e("Example", "Disabling: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }).start();
    }

    public void release() {
        new Thread(() -> {
            try {
                _device.release();
                _status.postValue(Status.OPENED);
            } catch (APosException e) {
                Log.e("Example", "Releasing: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }).start();
    }

    public void close() {
        new Thread(() -> {
            try {
                _device.close();
                _status.postValue(Status.CLOSED);
            } catch (APosException e) {
                Log.e("Example", "Closing: ", e);
                _errorMessage = e.getMessage();
                _status.postValue(Status.ERROR);
            }
        }).start();
    }

    public String getProductName() throws APosException {
        return _device.getPhysicalDeviceName();
    }

    public String getAdditionalData() throws APosException {
        return _device.getDeviceServiceDescription();
    }

    public LiveData<int[]> getDeviceFirmwareVersion() {
        if (_firmwareInfo == null) {
            _firmwareInfo = new MutableLiveData<>();
        }
        return _firmwareInfo;
    }

    public LiveData<Boolean> isUpgradable() {
        if (_isUpgradable == null) {
            _isUpgradable = new MutableLiveData<>();
            _isUpgradable.postValue(false);
        }
        return _isUpgradable;
    }

    public LiveData<Boolean> isOnline() {
        if (_online == null) {
            _online = new MutableLiveData<>();
            _online.postValue(false);
        }
        return _online;
    }

    public LiveData<Status> getStatus() {
        if (_status == null) {
            _status = new MutableLiveData<>();
            _status.postValue(Status.CLOSED);
        }
        return _status;
    }

    public LiveData<String> getBarcodeContent() {
        if (_barcodeContent == null) {
            _barcodeContent = new MutableLiveData<>();
        }
        return _barcodeContent;
    }

    public LiveData<String> getBarcodeSymbology() {
        if (_barcodeSymbology == null) {
            _barcodeSymbology = new MutableLiveData<>();
        }
        return _barcodeSymbology;
    }

    public LiveData<Integer> getConnectionStatus() {
        if (_connectionStatus == null)
            _connectionStatus = new MutableLiveData<>();
        return _connectionStatus;
    }

    public LiveData<String> getStatistics() {
        if (_statistics == null) {
            _statistics = new MutableLiveData<>();
        }
        return _statistics;
    }

    public LiveData<Double> getWeight() {
        if (_weight == null) {
            _weight = new MutableLiveData<>();
        }
        return _weight;
    }

    public String getErrorMessage() {
        return _errorMessage;
    }

    public StatisticsFormat getStatisticFormat() {
        return statisticsFormat;
    }

    public void setStatisticsFormat(StatisticsFormat format) {
        statisticsFormat = format;
    }

    public boolean isDeviceAScale() {
        return ((_device != null) && (_device instanceof Scale));
    }

    public LiveData<List<String>> getProfiles(Context cxt) {
        if (_profileIDs == null) {
            _profileIDs = new MutableLiveData<>();
            new Thread(() -> {
                try {
                    DLAPosConfigHelper.getInstance(cxt).initialize(cxt);
                } catch (APosException e) {
                    _errorMessage = e.getMessage();
                    _status.postValue(Status.ERROR);
                }
                _profileIDs.postValue(DLAPosConfigHelper.getInstance(cxt).getProfileManager().getAllProfileIds());
            }).start();
        }
        return _profileIDs;
    }

    public void cleanDatabase(Context cxt) {
        new Thread(() -> {
            if (DLAPosConfigHelper.getInstance(cxt).isInitialized()) {
                DLAPosConfigHelper.getInstance(cxt).getProfileManager().deleteAllProfiles();
            }
        }).start();
    }

}
