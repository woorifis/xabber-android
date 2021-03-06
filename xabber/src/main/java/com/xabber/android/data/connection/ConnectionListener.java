package com.xabber.android.data.connection;

import com.xabber.android.data.Application;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.connection.listeners.OnConnectedListener;
import com.xabber.android.data.connection.listeners.OnDisconnectListener;
import com.xabber.android.data.extension.blocking.BlockingManager;
import com.xabber.android.data.extension.carbons.CarbonManager;
import com.xabber.android.data.extension.httpfileupload.HttpFileUploadManager;
import com.xabber.android.data.extension.mam.MamManager;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.roster.PresenceManager;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.sasl.SASLErrorException;

class ConnectionListener implements org.jivesoftware.smack.ConnectionListener {

    @SuppressWarnings("WeakerAccess")
    ConnectionItem connectionItem;

    ConnectionListener(ConnectionItem connectionItem) {
        this.connectionItem = connectionItem;
    }

    private String getLogTag() {
        StringBuilder logTag = new StringBuilder();
        logTag.append(getClass().getSimpleName());

        if (connectionItem != null) {
            logTag.append(": ");
            logTag.append(connectionItem.getAccount());
        }
        return logTag.toString();
    }

    @Override
    public void connected(XMPPConnection connection) {
        LogManager.i(getLogTag(), "connected");

        Application.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectionItem.updateState(ConnectionState.authentication);

                for (OnConnectedListener listener : Application.getInstance().getManagers(OnConnectedListener.class)) {
                    listener.onConnected(connectionItem);
                }
            }
        });
    }

    @Override
    public void authenticated(XMPPConnection connection, final boolean resumed) {
        LogManager.i(getLogTag(), "authenticated. resumed: " + resumed);

        connectionItem.updateState(ConnectionState.connected);

        // just to see the order of call

        CarbonManager.getInstance().onAuthorized(connectionItem);
        MamManager.getInstance().onAuthorized(connectionItem);
        BlockingManager.getInstance().onAuthorized(connectionItem);
        HttpFileUploadManager.getInstance().onAuthorized(connectionItem);

        PresenceManager.getInstance().onAuthorized(connectionItem);

        Application.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AccountManager.getInstance().removeAccountError(connectionItem.getAccount());
            }
        });
    }

    @Override
    public void connectionClosed() {
        LogManager.i(getLogTag(), "connectionClosed");

        Application.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectionItem.updateState(ConnectionState.offline);

                for (OnDisconnectListener listener
                        : Application.getInstance().getManagers(OnDisconnectListener.class)) {
                    listener.onDisconnect(connectionItem);
                }
            }
        });
    }

    // going to reconnect with Smack Reconnection manager
    @Override
    public void connectionClosedOnError(final Exception e) {
        LogManager.i(getLogTag(), "connectionClosedOnError " + e + " " + e.getMessage());

        Application.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectionItem.updateState(ConnectionState.waiting);

                if (e instanceof SASLErrorException) {
                    AccountManager.getInstance().setEnabled(connectionItem.getAccount(), false);
                }
            }
        });
    }

    @Override
    public void reconnectionSuccessful() {
        LogManager.i(getLogTag(), "reconnectionSuccessful");
    }

    @Override
    public void reconnectingIn(final int seconds) {
        LogManager.i(getLogTag(), "reconnectionSuccessful");

        Application.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (connectionItem.getState() != ConnectionState.waiting && !connectionItem.getConnection().isAuthenticated()
                        && !connectionItem.getConnection().isConnected()) {
                    connectionItem.updateState(ConnectionState.waiting);
                }
            }
        });
    }

    @Override
    public void reconnectionFailed(final Exception e) {
        LogManager.i(getLogTag(), "reconnectionFailed " + e + " " + e.getMessage());

        Application.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectionItem.updateState(ConnectionState.offline);
            }
        });
    }
}
