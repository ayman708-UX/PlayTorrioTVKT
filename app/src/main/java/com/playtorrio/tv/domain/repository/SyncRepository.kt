package com.playtorrio.tv.domain.repository

import com.playtorrio.tv.data.remote.supabase.ClaimSyncResult
import com.playtorrio.tv.data.remote.supabase.SupabaseLinkedDevice

interface SyncRepository {
    suspend fun generateSyncCode(pin: String): Result<String>
    suspend fun getSyncCode(pin: String): Result<String>
    suspend fun claimSyncCode(code: String, pin: String, deviceName: String?): Result<ClaimSyncResult>
    suspend fun unlinkDevice(deviceUserId: String): Result<Unit>
    suspend fun getLinkedDevices(): Result<List<SupabaseLinkedDevice>>
}
