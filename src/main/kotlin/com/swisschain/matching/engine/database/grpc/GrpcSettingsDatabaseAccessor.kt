package com.swisschain.matching.engine.database.grpc

import com.google.protobuf.BoolValue
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.daos.setting.Setting
import com.swisschain.matching.engine.daos.setting.SettingsGroup
import com.swisschain.matching.engine.database.SettingsDatabaseAccessor
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class GrpcSettingsDatabaseAccessor(private val grpcConnectionString: String): SettingsDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcSettingsDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub: GrpcSettingsServiceGrpc.GrpcSettingsServiceBlockingStub = GrpcSettingsServiceGrpc.newBlockingStub(channel)

    override fun getSetting(settingGroup: AvailableSettingGroup, settingName: String, enabled: Boolean?): Setting? {
        try {
            val response = grpcStub.getSetting(convertToSettingRequest(settingGroup, settingName, enabled))
            return if (response != null) {
                Setting(response.name, response.value, response.enabled)
            } else {
                null
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to get setting $settingGroup, $settingName, $enabled", e)
            channel.shutdown()
            initConnection()
        }
        return null
    }

    private fun convertToSettingRequest(settingGroup: AvailableSettingGroup, settingName: String, enabled: Boolean?): GrpcSettings.SettingRequest {
        val builder = GrpcSettings.SettingRequest.newBuilder()
                .setGroup(settingGroup.settingGroupName).setName(settingName)
        if (enabled != null) {
            builder.enabled = BoolValue.of(enabled)
        }
        return builder.build()
    }

    override fun getSettingsGroup(settingGroup: AvailableSettingGroup, enabled: Boolean?): SettingsGroup? {
        try {
            val response = grpcStub.getSettingsGroup(convertToSettingsGroupRequest(settingGroup, enabled))
            return if (response != null) {
                convertToSettingsGroup(response)
            } else {
                null
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to get settings group $settingGroup, $enabled", e)
            channel.shutdown()
            initConnection()
        }
        return null
    }

    private fun convertToSettingsGroupRequest(settingGroup: AvailableSettingGroup, enabled: Boolean?): GrpcSettings.SettingsGroupRequest? {
        val builder = GrpcSettings.SettingsGroupRequest.newBuilder().setGroup(settingGroup.settingGroupName)
        if (enabled != null) {
            builder.enabled = BoolValue.of(enabled)
        }
        return builder.build()
    }

    private fun convertToSettingsGroup(src: GrpcSettings.SettingsGroup): SettingsGroup {
        val settings = HashSet<Setting>()
        src.settingsList.forEach { settings.add(Setting(it.name, it.value, it.enabled))}
        return SettingsGroup(AvailableSettingGroup.getBySettingsGroupName(src.group), settings)
    }

    override fun getAllSettingGroups(enabled: Boolean?): Set<SettingsGroup> {
        val result = HashSet<SettingsGroup>()
       try {
            val response = grpcStub.getAllSettingsGroups(convertToAllSettingsGroupsRequest(enabled))
            response?.groupsList?.forEach { result.add(convertToSettingsGroup(it)) }
       } catch (e: Exception) {
           LOGGER.error("Unable to get all settings groups $enabled", e)
           channel.shutdown()
           initConnection()
       }
        return result
    }

    private fun convertToAllSettingsGroupsRequest(enabled: Boolean?): GrpcSettings.AllSettingsGroupsRequest {
        val builder = GrpcSettings.AllSettingsGroupsRequest.newBuilder()
        if (enabled != null) {
            builder.enabled = BoolValue.of(enabled)
        }
        return builder.build()
    }

    override fun createOrUpdateSetting(settingGroup: AvailableSettingGroup, setting: Setting) {
        try {
            grpcStub.createOrUpdateSetting(GrpcSettings.Setting.newBuilder().setGroup(settingGroup.settingGroupName)
                    .setName(setting.name).setValue(setting.value).setEnabled(setting.enabled).build())
        } catch (e: Exception) {
            LOGGER.error("Unable to createOrUpdateSetting $settingGroup, $setting", e)
            channel.shutdown()
            initConnection()
        }
    }

    override fun deleteSetting(settingGroup: AvailableSettingGroup, settingName: String) {
        try {
            grpcStub.deleteSetting(GrpcSettings.SettingDeleteRequest.newBuilder().setGroup(settingGroup.settingGroupName)
                    .setName(settingName).build())
        } catch (e: Exception) {
            LOGGER.error("Unable to delete setting $settingGroup, $settingName", e)
            channel.shutdown()
            initConnection()
        }
    }

    override fun deleteSettingsGroup(settingGroup: AvailableSettingGroup) {
        try {
            grpcStub.deleteSettingsGroup(GrpcSettings.SettingsGroupDeleteRequest.newBuilder().setGroup(settingGroup.settingGroupName).build())
        } catch (e: Exception) {
            LOGGER.error("Unable to delete group $settingGroup", e)
            channel.shutdown()
            initConnection()
        }
    }

    @Synchronized
    private fun initConnection() {
        channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        grpcStub = GrpcSettingsServiceGrpc.newBlockingStub(channel)
    }
}