package com.swisschain.matching.engine.database.grpc

import com.swisschain.matching.engine.daos.setting.SettingHistoryRecord
import com.swisschain.matching.engine.database.SettingsHistoryDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.outgoing.messages.v2.toDate
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.LinkedList

class GrpcSettingsHistoryDatabaseAccessor(private val grpcConnectionString: String): SettingsHistoryDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcSettingsHistoryDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub: GrpcSettingsHistoryServiceGrpc.GrpcSettingsHistoryServiceBlockingStub = GrpcSettingsHistoryServiceGrpc.newBlockingStub(channel)

    override fun save(settingHistoryRecord: SettingHistoryRecord) {
        try {
            grpcStub.save(convertToSettingHistort(settingHistoryRecord))
        } catch (e: Exception) {
            LOGGER.error("Unable to save settings history $settingHistoryRecord", e)
            channel.shutdown()
            initConnection()
        }
    }

    private fun convertToSettingHistort(settingHistoryRecord: SettingHistoryRecord): GrpcSettingsHistory.SettingHistory {
        with (settingHistoryRecord) {
            return GrpcSettingsHistory.SettingHistory.newBuilder().setGroup(settingGroupName).setName(name).setValue(value)
                    .setEnabled(enabled).setComment(comment).setUser(user).setTimestamp(timestamp.createProtobufTimestampBuilder()).build()
        }
    }

    override fun get(settingGroupName: String, settingName: String): List<SettingHistoryRecord> {
        val result = LinkedList<SettingHistoryRecord>()
        try {
            val response = grpcStub.get(
                    GrpcSettingsHistory.SettingHistoryRequest.newBuilder().setGroup(settingGroupName).setName(settingName).build())
            response.groupsList.forEach { result.add(convertToSettingHistoryRecord(it)) }
        } catch (e: Exception) {
            LOGGER.error("Unable to get settings history $settingGroupName, $settingName", e)
            channel.shutdown()
            initConnection()
        }
        return result
    }

    override fun getAll(settingGroupName: String): List<SettingHistoryRecord> {
        val result = LinkedList<SettingHistoryRecord>()
        try {
            val response = grpcStub.getAll(
                    GrpcSettingsHistory.AllSettingHistoryRequest.newBuilder().setGroup(settingGroupName).build())
            response.groupsList.forEach { result.add(convertToSettingHistoryRecord(it)) }
        } catch (e: Exception) {
            LOGGER.error("Unable to get all settings history $settingGroupName", e)
            channel.shutdown()
            initConnection()
        }
        return result
    }

    private fun convertToSettingHistoryRecord(history: GrpcSettingsHistory.SettingHistory): SettingHistoryRecord {
        with (history) {
            return SettingHistoryRecord(group, name, value, enabled, comment, user, timestamp.toDate())
        }
    }

    @Synchronized
    private fun initConnection(): GrpcSettingsHistoryServiceGrpc.GrpcSettingsHistoryServiceBlockingStub {
        val channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        return GrpcSettingsHistoryServiceGrpc.newBlockingStub(channel)
    }
}