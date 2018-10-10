package no.nordicsemi.android.nrfmeshprovisioner.viewmodels;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import no.nordicsemi.android.log.LogSession;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.meshprovisioner.BaseMeshNode;
import no.nordicsemi.android.meshprovisioner.MeshManagerApi;
import no.nordicsemi.android.meshprovisioner.MeshManagerTransportCallbacks;
import no.nordicsemi.android.meshprovisioner.MeshProvisioningStatusCallbacks;
import no.nordicsemi.android.meshprovisioner.MeshStatusCallbacks;
import no.nordicsemi.android.meshprovisioner.meshmessagestates.MeshModel;
import no.nordicsemi.android.meshprovisioner.meshmessagestates.ProvisionedMeshNode;
import no.nordicsemi.android.meshprovisioner.messages.ConfigAppKeyAdd;
import no.nordicsemi.android.meshprovisioner.messages.ConfigAppKeyStatus;
import no.nordicsemi.android.meshprovisioner.messages.ConfigCompositionDataGet;
import no.nordicsemi.android.meshprovisioner.messages.ConfigCompositionDataStatus;
import no.nordicsemi.android.meshprovisioner.messages.ConfigModelAppStatus;
import no.nordicsemi.android.meshprovisioner.messages.ConfigModelPublicationStatus;
import no.nordicsemi.android.meshprovisioner.messages.ConfigModelSubscriptionStatus;
import no.nordicsemi.android.meshprovisioner.messages.ConfigNodeResetStatus;
import no.nordicsemi.android.meshprovisioner.messages.GenericLevelStatus;
import no.nordicsemi.android.meshprovisioner.messages.GenericOnOffStatus;
import no.nordicsemi.android.meshprovisioner.messages.MeshMessage;
import no.nordicsemi.android.meshprovisioner.messages.VendorModelMessageStatus;
import no.nordicsemi.android.meshprovisioner.models.SigModelParser;
import no.nordicsemi.android.meshprovisioner.provisionerstates.ProvisioningCapabilities;
import no.nordicsemi.android.meshprovisioner.provisionerstates.ProvisioningState;
import no.nordicsemi.android.meshprovisioner.provisionerstates.UnprovisionedMeshNode;
import no.nordicsemi.android.meshprovisioner.utils.Element;
import no.nordicsemi.android.nrfmeshprovisioner.adapter.ExtendedBluetoothDevice;
import no.nordicsemi.android.nrfmeshprovisioner.ble.BleMeshManager;
import no.nordicsemi.android.nrfmeshprovisioner.ble.BleMeshManagerCallbacks;
import no.nordicsemi.android.nrfmeshprovisioner.livedata.SingleLiveEvent;
import no.nordicsemi.android.nrfmeshprovisioner.livedata.TransactionFailedLiveData;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanRecord;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

import static no.nordicsemi.android.nrfmeshprovisioner.ble.BleMeshManager.MESH_PROXY_UUID;

@SuppressWarnings("unused")
public class NrfMeshRepository implements MeshProvisioningStatusCallbacks, MeshStatusCallbacks, MeshManagerTransportCallbacks, BleMeshManagerCallbacks {

    private static final String TAG = NrfMeshRepository.class.getSimpleName();

    /**
     * Connection States Connecting, Connected, Disconnecting, Disconnected etc.
     **/
    private final MutableLiveData<Boolean> mIsConnectedToMesh = new MutableLiveData<>();

    /**
     * Live data flag containing connected state.
     **/
    private MutableLiveData<Boolean> mIsConnected;

    /**
     * LiveData to notify when device is ready
     **/
    private final MutableLiveData<Void> mOnDeviceReady = new MutableLiveData<>();

    /**
     * Updates the connection state while connecting to a peripheral
     **/
    private final MutableLiveData<String> mConnectionState = new MutableLiveData<>();

    /**
     * Flag to determine if a reconnection is in the progress when provisioning has completed
     **/
    private final MutableLiveData<Boolean> mIsReconnecting = new MutableLiveData<>();

    private final MutableLiveData<ProvisioningCapabilities> capabilitiesMutableLiveData = new MutableLiveData<>();

    /**
     * Flag to determine if a reconnection is in the progress when provisioning has completed
     **/
    private final MutableLiveData<byte[]> mConfigurationSrc = new MutableLiveData<>();

    private final MutableLiveData<BaseMeshNode> mMeshNodeLiveData = new MutableLiveData<>();

    private final NetworkInformation mNetworkInformation;

    /**
     * Contains the initial provisioning live data
     **/
    private NetworkInformationLiveData mNetworkInformationLiveData;

    /**
     * Contains the initial provisioning live data
     **/
    private final ProvisioningSettingsLiveData mProvisioningLiveData = new ProvisioningSettingsLiveData();

    /**
     * Flag to determine if provisioning was completed
     **/
    private boolean mIsProvisioningComplete = false;

    /**
     * Contains the {@link ExtendedMeshNode}
     **/
    private ExtendedMeshNode mExtendedMeshNode;

    /**
     * Contains the {@link ExtendedElement}
     **/
    private ExtendedElement mExtendedElement;

    /**
     * Contains the {@link ExtendedMeshModel}
     **/
    private ExtendedMeshModel mExtendedMeshModel;

    /**
     * Mesh model to configure
     **/
    final MutableLiveData<MeshModel> mMeshModel = new MutableLiveData<>();

    /**
     * Mesh model to configure
     **/
    final MutableLiveData<Element> mElement = new MutableLiveData<>();

    /**
     * App key add status
     **/
    final SingleLiveEvent<ConfigCompositionDataStatus> mCompositionDataStatus = new SingleLiveEvent<>();

    /**
     * App key add status
     **/
    final SingleLiveEvent<ConfigAppKeyStatus> mAppKeyStatus = new SingleLiveEvent<>();

    /**
     * App key bind status
     **/
    final SingleLiveEvent<ConfigModelAppStatus> mAppKeyBindStatus = new SingleLiveEvent<>();

    /**
     * publication status
     **/
    final SingleLiveEvent<ConfigModelPublicationStatus> mConfigModelPublicationStatus = new SingleLiveEvent<>();

    /**
     * Subscription bind status
     **/
    final SingleLiveEvent<ConfigModelSubscriptionStatus> mConfigModelSubscriptionStatus = new SingleLiveEvent<>();

    /**
     * Contains the initial provisioning live data
     **/
    private ProvisioningSettingsLiveData mProvisioningSettingsLiveData;

    private MeshMessageLiveData mMeshMessageLiveData = new MeshMessageLiveData();
    /**
     * Contains the provisioned nodes
     **/
    private final MutableLiveData<Map<Integer, ProvisionedMeshNode>> mProvisionedNodes = new MutableLiveData<>();

    private final TransactionFailedLiveData mTransactionFailedLiveData = new TransactionFailedLiveData();

    private static NrfMeshRepository mNrfMeshRepository;
    private final MeshManagerApi mMeshManagerApi;
    private final BleMeshManager mBleMeshManager;
    private BaseMeshNode mMeshNode;
    private boolean mIsReconnectingFlag;
    private final Handler mHandler;
    private boolean mIsScanning;
    private boolean mSetupProvisionedNode;
    private ProvisioningStatusLiveData mProvisioningStateLiveData;

    private final Runnable mReconnectRunnable = this::startScan;

    private final Runnable mScannerTimeout = this::stopScan;

    private NrfMeshRepository(final MeshManagerApi meshManagerApi, final NetworkInformation networkInformation, final BleMeshManager bleMeshManager) {
        //Initialize the mesh api
        mMeshManagerApi = meshManagerApi;
        mMeshManagerApi.setProvisionerManagerTransportCallbacks(this);
        mMeshManagerApi.setProvisioningStatusCallbacks(this);
        mMeshManagerApi.setMeshStatusCallbacks(this);
        //Load live data with provisioned nodes
        mProvisionedNodes.postValue(mMeshManagerApi.getProvisionedNodes());
        //Load live data with provisioning settings
        mProvisioningSettingsLiveData = new ProvisioningSettingsLiveData(mMeshManagerApi.getProvisioningSettings());
        //Load live data with configuration address
        mConfigurationSrc.postValue(mMeshManagerApi.getConfiguratorSrc());

        //Initialize the ble manager
        mBleMeshManager = bleMeshManager;
        mBleMeshManager.setGattCallbacks(this);

        mNetworkInformation = networkInformation;
        //Load live data with network information
        mNetworkInformationLiveData = new NetworkInformationLiveData(mNetworkInformation);
        mHandler = new Handler();
    }

    public static NrfMeshRepository getInstance(final MeshManagerApi meshManagerApi, final NetworkInformation networkInformation, final BleMeshManager bleMeshManager) {
        if (mNrfMeshRepository == null) {
            mNrfMeshRepository = new NrfMeshRepository(meshManagerApi, networkInformation, bleMeshManager);
        }
        return mNrfMeshRepository;
    }

    /**
     * Returns {@link SingleLiveEvent} containing the device ready state.
     */
    LiveData<Void> isDeviceReady() {
        return mOnDeviceReady;
    }

    /**
     * Returns {@link SingleLiveEvent} containing the device ready state.
     */
    public LiveData<String> getConnectionState() {
        return mConnectionState;
    }

    /**
     * Returns {@link SingleLiveEvent} containing the device ready state.
     */
    public LiveData<Boolean> isConnected() {
        return mIsConnected;
    }

    public LiveData<Boolean> isReconnecting() {
        return mIsReconnecting;
    }

    boolean isProvisioningComplete() {
        return mIsProvisioningComplete;
    }

    LiveData<Boolean> isConnectedToNetwork() {
        return mIsConnectedToMesh;
    }

    public LiveData<Map<Integer, ProvisionedMeshNode>> getProvisionedNodes() {
        return mProvisionedNodes;
    }

    final ProvisioningSettingsLiveData getProvisioningSettingsLiveData() {
        return mProvisioningSettingsLiveData;
    }

    NetworkInformationLiveData getNetworkInformationLiveData() {
        return mNetworkInformationLiveData;
    }

    LiveData<byte[]> getConfigurationSrcLiveData() {
        return mConfigurationSrc;
    }

    public LiveData<ProvisioningCapabilities> getCapabilitiesMutableLiveData() {
        return capabilitiesMutableLiveData;
    }

    boolean setConfiguratorSrc(final byte[] configuratorSrc) {
        if (mMeshManagerApi.setConfiguratorSrc(configuratorSrc)) {
            mConfigurationSrc.postValue(mMeshManagerApi.getConfiguratorSrc());
            return true;
        }
        return false;
    }

    ProvisioningStatusLiveData getProvisioningState() {
        return mProvisioningStateLiveData;
    }

    TransactionFailedLiveData getTransactionFailedLiveData() {
        return mTransactionFailedLiveData;
    }
    /**
     * Returns the mesh manager api
     *
     * @return {@link MeshManagerApi}
     */
    MeshManagerApi getMeshManagerApi() {
        return mMeshManagerApi;
    }

    /**
     * Returns the {@link MeshMessageLiveData} live data object containing the mesh message
     */
    MeshMessageLiveData getMeshMessageLiveData() {
        return mMeshMessageLiveData;
    }

    /**
     * Reset mesh network
     */
    void resetMeshNetwork() {
        disconnect();
        mMeshManagerApi.resetMeshNetwork();
        mProvisionedNodes.postValue(mMeshManagerApi.getProvisionedNodes());
        mNetworkInformation.refreshProvisioningData();
        mProvisioningLiveData.reset(mMeshManagerApi.getProvisioningSettings());
    }

    /**
     * Connect to peripheral
     *
     * @param device bluetooth device
     */
    public void connect(final Context context, final ExtendedBluetoothDevice device, final boolean connectToNetwork) {
        mNetworkInformationLiveData.getValue().setNodeName(device.getName());
        mIsProvisioningComplete = false;
        final LogSession logSession = Logger.newSession(context, null, device.getAddress(), device.getName());
        mBleMeshManager.setLogger(logSession);
        final BluetoothDevice bluetoothDevice = device.getDevice();
        initIsConnectedLiveData(connectToNetwork);
        mBleMeshManager.connect(bluetoothDevice);
    }

    /**
     * Connect to peripheral
     *
     * @param device bluetooth device
     */
    private void connect(final ExtendedBluetoothDevice device) {
        mBleMeshManager.connect(device.getDevice());
    }

    private void initIsConnectedLiveData(final boolean connectToNetwork) {
        if (connectToNetwork) {
            mIsConnected = new SingleLiveEvent<>();
        } else {
            mIsConnected = new MutableLiveData<>();
        }
    }

    /**
     * Disconnects from peripheral
     */
    public void disconnect() {
        clearMeshNodeLiveData();
        removeCallbacks();
        mIsProvisioningComplete = false;
        mBleMeshManager.disconnect();
    }

    void removeCallbacks() {
        mHandler.removeCallbacksAndMessages(null);
    }

    void clearMeshNodeLiveData() {
        mMeshNodeLiveData.setValue(null);
    }

    LiveData<BaseMeshNode> getBaseMeshNode() {
        return mMeshNodeLiveData;
    }

    /**
     * Returns the selected mesh node
     */
    ExtendedMeshNode getSelectedMeshNode() {
        return mExtendedMeshNode;
    }

    /**
     * Sets the mesh node to be configured
     *
     * @param node provisioned mesh node
     */
    void setSelectedMeshNode(final ProvisionedMeshNode node) {
        if (mExtendedMeshNode == null) {
            mExtendedMeshNode = new ExtendedMeshNode(node);
        } else {
            mExtendedMeshNode.updateMeshNode(node);
        }
    }

    /**
     * Returns the selected element
     */
    ExtendedElement getSelectedElement() {
        return mExtendedElement;
    }

    /**
     * Set the selected {@link Element} to be configured
     *
     * @param element element
     */
    void setSelectedElement(final Element element) {
        if (mExtendedElement == null) {
            mExtendedElement = new ExtendedElement(element);
        } else {
            mExtendedElement.setElement(element);
        }
    }

    /**
     * Returns the selected mesh model
     */
    ExtendedMeshModel getSelectedModel() {
        return mExtendedMeshModel;
    }

    /**
     * Set the selected model to be configured
     *
     * @param model mesh model
     */
    void setSelectedModel(final MeshModel model) {
        if (mExtendedMeshModel == null) {
            mExtendedMeshModel = new ExtendedMeshModel(model);
        } else {
            mExtendedMeshModel.setMeshModel(model);
        }
    }

    void sendGetCompositionData() {
        final ProvisionedMeshNode node = (ProvisionedMeshNode) mExtendedMeshNode.getMeshNode();
        final ConfigCompositionDataGet configCompositionDataGet = new ConfigCompositionDataGet(node, 0);
        mMeshManagerApi.getCompositionData(configCompositionDataGet);
    }

    void sendAppKeyAdd(final ConfigAppKeyAdd configAppKeyAdd) {
        mMeshManagerApi.addAppKey(configAppKeyAdd);
    }

    @Override
    public void onDataReceived(final BluetoothDevice bluetoothDevice, final int mtu, final byte[] pdu) {
        final BaseMeshNode node = mMeshNode = mExtendedMeshNode.getMeshNode();
        mMeshManagerApi.handleNotifications(node, mtu, pdu);
    }

    @Override
    public void onDataSent(final BluetoothDevice device, final int mtu, final byte[] pdu) {
        final BaseMeshNode node = mMeshNode;
        mMeshManagerApi.handleWrites(node, mtu, pdu);
    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
        mConnectionState.postValue("Connecting....");
    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        mIsConnected.postValue(true);
        mConnectionState.postValue("Discovering services....");

    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
        mConnectionState.postValue("Disconnecting...");
        if (mIsReconnectingFlag) {
            mIsConnected.postValue(false);
        }
        mSetupProvisionedNode = false;
        mIsConnectedToMesh.postValue(false);
    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        mConnectionState.postValue("Disconnected");
        if (mIsReconnectingFlag) {
            mIsReconnectingFlag = false;
            mIsReconnecting.postValue(false);
        } else {
            mIsConnected.postValue(false);
        }
        mOnDeviceReady.postValue(null);
        mSetupProvisionedNode = false;
    }

    @Override
    public void onLinklossOccur(final BluetoothDevice device) {
        mIsConnected.postValue(false);
    }

    @Override
    public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
        mConnectionState.postValue("Initializing...");
    }

    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        mOnDeviceReady.postValue(null);

        if (mBleMeshManager.isProvisioningComplete()) {
            if (mSetupProvisionedNode) {
                //We update the bluetooth device after a startScan because some devices may start advertising with different mac address
                mMeshNode.setBluetoothDeviceAddress(device.getAddress());
                //Adding a slight delay here so we don't send anything before we receive the mesh beacon message
                mHandler.postDelayed(() -> mMeshManagerApi.getCompositionData((ProvisionedMeshNode) mMeshNode), 2000);
            }
            mIsConnectedToMesh.postValue(true);
        }
    }

    @Override
    public boolean shouldEnableBatteryLevelNotifications(final BluetoothDevice device) {
        return false;
    }

    @Override
    public void onBatteryValueReceived(final BluetoothDevice device, final int value) {

    }

    @Override
    public void onBondingRequired(final BluetoothDevice device) {

    }

    @Override
    public void onBonded(final BluetoothDevice device) {

    }

    @Override
    public void onError(final BluetoothDevice device, final String message, final int errorCode) {
    }

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {

    }

    @Override
    public void sendPdu(final BaseMeshNode meshNode, final byte[] pdu) {
        mBleMeshManager.sendPdu(pdu);
    }

    @Override
    public int getMtu() {
        return mBleMeshManager.getMtuSize();
    }

    @Override
    public void onProvisioningInviteSent(final UnprovisionedMeshNode node) {
        mMeshNode = node;
        mMeshNodeLiveData.postValue(node);
    }

    @Override
    public void onProvisioningCapabilitiesReceived(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningStartSent(final UnprovisionedMeshNode node) {
        mMeshNode = node;

    }

    @Override
    public void onProvisioningPublicKeySent(final UnprovisionedMeshNode node) {
        mMeshNode = node;

    }

    @Override
    public void onProvisioningPublicKeyReceived(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningAuthenticationInputRequested(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningInputCompleteSent(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningConfirmationSent(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningConfirmationReceived(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningRandomSent(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningRandomReceived(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningDataSent(final UnprovisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningFailed(final UnprovisionedMeshNode node, final int errorCode) {
        mMeshNode = node;
    }

    @Override
    public void onProvisioningComplete(final ProvisionedMeshNode node) {
        mIsProvisioningComplete = true;
        node.setIsProvisioned(true);
        mMeshNode = node;
        mIsReconnectingFlag = true;
        mIsReconnecting.postValue(true);
        mBleMeshManager.disconnect();
        mBleMeshManager.refreshDeviceCache();
        mProvisionedNodes.postValue(mMeshManagerApi.getProvisionedNodes());
        mHandler.postDelayed(mReconnectRunnable, 1500); //Added a slight delay to disconnect and refresh the cache
    }

    @Override
    public void onProvisioningStateChanged(final BaseMeshNode meshNode, final ProvisioningState.States state, final byte[] data) {
        mMeshNode = meshNode;
        mMeshNodeLiveData.postValue(meshNode);
        switch (state) {
            case PROVISIONING_INVITE:
                mProvisioningStateLiveData = new ProvisioningStatusLiveData();
                break;
            case PROVISIONING_COMPLETE:
                onProvisioningCompleted(meshNode);
                break;
            case PROVISIONING_FAILED:
                mIsProvisioningComplete = false;
                break;
        }
        mProvisioningStateLiveData.onMeshNodeStateUpdated(state);
    }

    private void onProvisioningCompleted(final BaseMeshNode node) {
        mIsProvisioningComplete = true;
        node.setIsProvisioned(true);
        mMeshNode = node;
        mIsReconnectingFlag = true;
        mIsReconnecting.postValue(true);
        mBleMeshManager.disconnect();
        mBleMeshManager.refreshDeviceCache();
        mProvisionedNodes.postValue(mMeshManagerApi.getProvisionedNodes());
        mHandler.postDelayed(mReconnectRunnable, 1500); //Added a slight delay to disconnect and refresh the cache
    }

    @Override
    public void onTransactionFailed(final ProvisionedMeshNode node, final int src, final boolean hasIncompleteTimerExpired) {
        mMeshNode = node;
    }

    @Override
    public void onUnknownPduReceived(final ProvisionedMeshNode node) {
        mMeshNode = node;
    }

    @Override
    public void onBlockAcknowledgementSent(final ProvisionedMeshNode node) {
        mMeshNode = node;
        if (mSetupProvisionedNode) {
            mMeshNodeLiveData.postValue(node);
            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisioningState.States.SENDING_BLOCK_ACKNOWLEDGEMENT);
        }
    }

    @Override
    public void onBlockAcknowledgementReceived(final ProvisionedMeshNode node) {
        mMeshNode = node;
        if (mSetupProvisionedNode) {
            mMeshNodeLiveData.postValue(node);
            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisioningState.States.BLOCK_ACKNOWLEDGEMENT_RECEIVED);
        }
    }

    @Override
    public void onGetCompositionDataSent(@NonNull final ProvisionedMeshNode node) {
        mMeshNode = node;
        if (mSetupProvisionedNode) {
            mMeshNodeLiveData.postValue(node);
            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisioningState.States.COMPOSITION_DATA_GET_SENT);
        }
    }

    @Override
    public void onCompositionDataStatusReceived(@NonNull final ConfigCompositionDataStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        if (mSetupProvisionedNode) {
            mMeshNodeLiveData.postValue(node);
            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisioningState.States.COMPOSITION_DATA_STATUS_RECEIVED);
            //We send app key add after composition is complete. Adding a delay so that we don't send anything before the acknowledgement is sent out.
            if (!mMeshManagerApi.getProvisioningSettings().getAppKeys().isEmpty()) {
                mHandler.postDelayed(() -> {
                    final String appKey = mProvisioningSettingsLiveData.getSelectedAppKey();
                    final int index = mMeshManagerApi.getProvisioningSettings().getAppKeys().indexOf(appKey);
                    mMeshManagerApi.addAppKey(node, index, appKey);
                }, 2500);
            }
        } else {
            mExtendedMeshNode.updateMeshNode(node);
            mMeshMessageLiveData.postValue(status);
        }
    }

    @Override
    public void onAppKeyAddSent(final ProvisionedMeshNode node) {
        mMeshNode = node;
        if (mSetupProvisionedNode) {
            mMeshNodeLiveData.postValue(node);
            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisioningState.States.SENDING_APP_KEY_ADD);
        } else {
            mExtendedMeshNode.updateMeshNode(node);
        }
    }

    @Override
    public void onAppKeyStatusReceived(final ConfigAppKeyStatus status) {
        if (mSetupProvisionedNode) {
            mSetupProvisionedNode = false;
            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisioningState.States.APP_KEY_STATUS_RECEIVED);
        } else {
            final ProvisionedMeshNode node = status.getMeshNode();
            mMeshNode = node;
            mExtendedMeshNode.updateMeshNode(node);
            mMeshMessageLiveData.postValue(status);
        }
    }

    @Override
    public void onAppKeyBindSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onAppKeyUnbindSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onAppKeyBindStatusReceived(@NonNull final ConfigModelAppStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        final int elementAddress = status.getElementAddress();
        final int modelId = status.getModelIdentifier();
        mExtendedMeshModel.setMeshModel(node.getElements().get(elementAddress).getMeshModels().get(modelId));
        mMeshMessageLiveData.postValue(status);
    }

    @Override
    public void onPublicationSetSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onPublicationStatusReceived(@NonNull final ConfigModelPublicationStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        final int elementAddress = status.getElementAddress();
        final int modelId = status.getModelIdentifier();
        mExtendedMeshModel.setMeshModel(node.getElements().get(elementAddress).getMeshModels().get(modelId));
        mMeshMessageLiveData.postValue(status);
    }

    @Override
    public void onSubscriptionAddSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onSubscriptionDeleteSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onSubscriptionStatusReceived(final ConfigModelSubscriptionStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        final int elementAddress = status.getElementAddress();
        final int modelId = status.getModelIdentifier();
        mExtendedMeshModel.setMeshModel(node.getElements().get(elementAddress).getMeshModels().get(modelId));
        mMeshMessageLiveData.postValue(status);
    }

    @Override
    public void onMeshNodeResetSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onMeshNodeResetStatusReceived(@NonNull final ConfigNodeResetStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        mMeshMessageLiveData.postValue(status);
    }

    @Override
    public void onGenericOnOffGetSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onGenericOnOffSetSent(final ProvisionedMeshNode node, final boolean presentOnOff, final boolean targetOnOff, final int remainingTime) {

    }

    @Override
    public void onGenericOnOffSetUnacknowledgedSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onGenericOnOffStatusReceived(final GenericOnOffStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        final int elementAddress = status.getSrcAddress();
        final Element element = node.getElements().get(elementAddress);
        final MeshModel model = element.getMeshModels().get((int)SigModelParser.GENERIC_ON_OFF_SERVER);
        mExtendedMeshModel.setMeshModel(model);
        mMeshMessageLiveData.postValue(status);
    }

    @Override
    public void onGenericLevelGetSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onGenericLevelSetSent(final ProvisionedMeshNode node, final boolean presentOnOff, final boolean targetOnOff, final int remainingTime) {

    }

    @Override
    public void onGenericLevelSetUnacknowledgedSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onGenericLevelStatusReceived(final GenericLevelStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        mMeshMessageLiveData.postValue(status);
    }

    @Override
    public void onUnacknowledgedVendorModelMessageSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onAcknowledgedVendorModelMessageSent(final ProvisionedMeshNode node) {

    }

    @Override
    public void onVendorModelMessageStatusReceived(final VendorModelMessageStatus status) {
        final ProvisionedMeshNode node = status.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        mMeshMessageLiveData.postValue(status);
    }

    @Override
    public void onMeshMessageSent(final MeshMessage meshMessage) {
        final ProvisionedMeshNode node = meshMessage.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
    }

    @Override
    public void onMeshMessageReceived(final MeshMessage meshMessage) {
        final ProvisionedMeshNode node = meshMessage.getMeshNode();
        mMeshNode = node;
        mExtendedMeshNode.updateMeshNode(node);
        mMeshMessageLiveData.postValue(meshMessage);
    }

    /**
     * Starts reconnecting to the device
     */
    private void startScan() {
        if (mIsScanning)
            return;

        mIsScanning = true;
        mConnectionState.postValue("Scanning for provisioned node");
        // Scanning settings
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                // Refresh the devices list every second
                .setReportDelay(0)
                // Hardware filtering has some issues on selected devices
                .setUseHardwareFilteringIfSupported(false)
                // Samsung S6 and S6 Edge report equal value of RSSI for all devices. In this app we ignore the RSSI.
                /*.setUseHardwareBatchingIfSupported(false)*/
                .build();

        // Let's use the filter to scan only for Blinky devices
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid((MESH_PROXY_UUID))).build());

        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        scanner.startScan(filters, settings, scanCallback);
        Log.v(TAG, "Scan started");
        mHandler.postDelayed(mScannerTimeout, 20000);
    }

    /**
     * stop scanning for bluetooth devices.
     */
    private void stopScan() {
        mHandler.removeCallbacks(mScannerTimeout);
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        scanner.stopScan(scanCallback);
        mIsScanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            //In order to connect to the correct device, the hash advertised in the advertisement data should be matched.
            //This is to make sure we connect to the same device as device addresses could change after provisioning.
            final ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                final byte[] serviceData = scanRecord.getServiceData(new ParcelUuid((MESH_PROXY_UUID)));
                if (serviceData != null) {
                    if (mMeshManagerApi.isAdvertisedWithNodeIdentity(serviceData)) {
                        final ProvisionedMeshNode node = (ProvisionedMeshNode) mMeshNode;
                        if (mMeshManagerApi.nodeIdentityMatches(node, serviceData)) {
                            stopScan();
                            mConnectionState.postValue("Provisioned node found");
                            onProvisionedDeviceFound(node, new ExtendedBluetoothDevice(result));
                        }
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            // Batch scan is disabled (report delay = 0)
        }

        @Override
        public void onScanFailed(final int errorCode) {

        }
    };

    private void onProvisionedDeviceFound(final ProvisionedMeshNode node, final ExtendedBluetoothDevice device) {
        mSetupProvisionedNode = true;
        node.setBluetoothDeviceAddress(device.getAddress());
        mMeshNode = node;
        connect(device);
    }

    BleMeshManager getBleMeshManager() {
        return mBleMeshManager;
    }
}