package app.settlezero.core.network

import app.settlezero.core.network.model.NetworkSyncOperation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SyncApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: SyncApiClient
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val url = mockWebServer.url("/").toString().removeSuffix("/")
        apiClient = SyncApiClient(
            baseUrl = url,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `healthCheck returns true on 200 OK`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok","service":"settlezero-backend","version":"1.0.0"}""")
        )

        val result = apiClient.healthCheck()
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun `syncBatch sends operations and parses batch response`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "results": [
                        {
                          "idempotencyKey": "key-1",
                          "status": "ACCEPTED"
                        },
                        {
                          "idempotencyKey": "key-2",
                          "status": "DUPLICATE"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val operations = listOf(
            NetworkSyncOperation(
                idempotencyKey = "key-1",
                groupId = "group-1",
                operationType = "CREATE_EXPENSE",
                payloadJson = "{}",
                createdAt = 1000L
            ),
            NetworkSyncOperation(
                idempotencyKey = "key-2",
                groupId = "group-1",
                operationType = "CREATE_EXPENSE",
                payloadJson = "{}",
                createdAt = 1001L
            )
        )

        val result = apiClient.syncBatch(operations)
        assertThat(result.isSuccess).isTrue()
        val batchRes = result.getOrThrow()
        assertThat(batchRes.results).hasSize(2)
        assertThat(batchRes.results[0].idempotencyKey).isEqualTo("key-1")
        assertThat(batchRes.results[0].status).isEqualTo("ACCEPTED")
        assertThat(batchRes.results[1].idempotencyKey).isEqualTo("key-2")
        assertThat(batchRes.results[1].status).isEqualTo("DUPLICATE")

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.path).isEqualTo("/api/v1/sync/batch")
        assertThat(recordedRequest.method).isEqualTo("POST")
    }

    @Test
    fun `getSnapshot retrieves group snapshot`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "group": {
                        "id": "grp-1",
                        "name": "Goa Trip",
                        "currencyCode": "INR",
                        "currencyScale": 2,
                        "status": "ACTIVE",
                        "inviteToken": "tok123",
                        "isCurrencyLocked": true,
                        "isReadOnly": false,
                        "createdAt": 1000,
                        "updatedAt": 1000
                      },
                      "members": [
                        {
                          "id": "mem-1",
                          "groupId": "grp-1",
                          "name": "Alice",
                          "role": "SUPER_ADMIN",
                          "status": "ACTIVE_CLAIMED",
                          "createdAt": 1000
                        }
                      ],
                      "expenses": [],
                      "settlements": [],
                      "activityHistory": []
                    }
                    """.trimIndent()
                )
        )

        val result = apiClient.getSnapshot("grp-1")
        assertThat(result.isSuccess).isTrue()
        val snapshot = result.getOrThrow()
        assertThat(snapshot.group.name).isEqualTo("Goa Trip")
        assertThat(snapshot.members).hasSize(1)
        assertThat(snapshot.members[0].name).isEqualTo("Alice")

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/v1/groups/grp-1/snapshot")
    }

    @Test
    fun `getInviteMetadata retrieves invite details`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "groupId": "grp-99",
                      "groupName": "Weekend Trek",
                      "currencyCode": "INR",
                      "currencyScale": 2,
                      "createdAt": 12345
                    }
                    """.trimIndent()
                )
        )

        val result = apiClient.getInviteMetadata("inv-token-xyz")
        assertThat(result.isSuccess).isTrue()
        val metadata = result.getOrThrow()
        assertThat(metadata.groupId).isEqualTo("grp-99")
        assertThat(metadata.groupName).isEqualTo("Weekend Trek")
        assertThat(metadata.currencyCode).isEqualTo("INR")

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/v1/invites/inv-token-xyz")
    }

    @Test
    fun `submitClaim posts claim request`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"PENDING","claimId":"claim-1"}""")
        )

        val result = apiClient.submitClaim("grp-1", "mem-2", "dev-abc", "123456")
        assertThat(result.isSuccess).isTrue()
        val claimRes = result.getOrThrow()
        assertThat(claimRes.status).isEqualTo("PENDING")
        assertThat(claimRes.claimId).isEqualTo("claim-1")

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/v1/groups/grp-1/claims")
        assertThat(recorded.body.readUtf8()).contains("123456")
    }
}

