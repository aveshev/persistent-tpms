package com.masselis.tpmsadvanced.interfaces

import android.app.backup.BackupAgent
import android.app.backup.BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseBackup

/**
 * Auto Backup, with a copy of car.db in its place (see [DatabaseBackup] and the backup rules): slim
 * for the cloud backup, full for a device-to-device transfer.
 * Full backup only (`android:fullBackupOnly`): the key/value callbacks are never called.
 */
public class SlimBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        try {
            DatabaseBackup.build(
                context = this,
                // Only the cloud backup has the 25 MB limit
                slim = (data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) == 0,
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Still backs up the settings: failing here would fail every backup the same way
            Log.e(TAG, "Unable to build the database's backup copy", e)
        }
        try {
            super.onFullBackup(data)
        } finally {
            DatabaseBackup.delete(this)
        }
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ): Unit = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ): Unit = Unit

    private companion object {
        const val TAG = "SlimBackupAgent"
    }
}
