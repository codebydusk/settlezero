package app.settlezero.core.network

import app.settlezero.core.network.model.NetworkClaimRequest
import app.settlezero.core.network.model.NetworkClaimResponse
import app.settlezero.core.network.model.NetworkGroupSnapshot
import app.settlezero.core.network.model.NetworkInviteMetadata
import app.settlezero.core.network.model.NetworkSyncBatchRequest
import app.settlezero.core.network.model.NetworkSyncBatchResponse
import app.settlezero.core.network.model.NetworkSyncOperation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

interface SyncNetworkDataSource {
    suspend fun healthCheck(): Result<Boolean>
    suspend fun syncBatch(operations: List<NetworkSyncOperation>): Result<NetworkSyncBatchResponse>
    suspend fun getSnapshot(groupId: String): Result<NetworkGroupSnapshot>
    suspend fun getInviteMetadata(token: String): Result<NetworkInviteMetadata>
    suspend fun submitClaim(groupId: String, memberId: String, claimantDeviceId: String, sasCode: String): Result<NetworkClaimResponse>
    suspend fun approveClaim(groupId: String, claimId: String): Result<Boolean>
}

class SyncApiClient(
    private var baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = defaultJson()
) : SyncNetworkDataSource {

    constructor(
        baseUrl: String = DEFAULT_BASE_URL,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        baseUrl = baseUrl,
        client = defaultOkHttpClient(),
        ioDispatcher = ioDispatcher,
        json = defaultJson()
    )

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun getBaseUrl(): String = baseUrl

    override suspend fun healthCheck(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl}/api/v1/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
    }

    override suspend fun syncBatch(operations: List<NetworkSyncOperation>): Result<NetworkSyncBatchResponse> = withContext(ioDispatcher) {
        runCatching {
            val batchReq = NetworkSyncBatchRequest(operations = operations)
            val bodyJson = json.encodeToString(batchReq)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("${baseUrl}/api/v1/sync/batch")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: $errorMsg")
                }
                val rawBody = response.body?.string() ?: throw IOException("Empty response body from sync batch")
                json.decodeFromString<NetworkSyncBatchResponse>(rawBody)
            }
        }
    }

    override suspend fun getSnapshot(groupId: String): Result<NetworkGroupSnapshot> = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl}/api/v1/groups/$groupId/snapshot")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: Failed to get snapshot for $groupId")
                }
                val rawBody = response.body?.string() ?: throw IOException("Empty response body for group snapshot")
                json.decodeFromString<NetworkGroupSnapshot>(rawBody)
            }
        }
    }

    override suspend fun getInviteMetadata(token: String): Result<NetworkInviteMetadata> = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl}/api/v1/invites/$token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: Invite token not found or invalid")
                }
                val rawBody = response.body?.string() ?: throw IOException("Empty response body for invite")
                json.decodeFromString<NetworkInviteMetadata>(rawBody)
            }
        }
    }

    override suspend fun submitClaim(
        groupId: String,
        memberId: String,
        claimantDeviceId: String,
        sasCode: String
    ): Result<NetworkClaimResponse> = withContext(ioDispatcher) {
        runCatching {
            val claimReq = NetworkClaimRequest(
                memberId = memberId,
                claimantDeviceId = claimantDeviceId,
                sasCode = sasCode
            )
            val bodyJson = json.encodeToString(claimReq)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("${baseUrl}/api/v1/groups/$groupId/claims")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: Failed to submit claim")
                }
                val rawBody = response.body?.string() ?: throw IOException("Empty response body for claim submission")
                json.decodeFromString<NetworkClaimResponse>(rawBody)
            }
        }
    }

    override suspend fun approveClaim(groupId: String, claimId: String): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            val emptyBody = "".toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("${baseUrl}/api/v1/groups/$groupId/claims/$claimId/approve")
                .post(emptyBody)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

