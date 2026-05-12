package com.github.logboard.log

import com.github.logboard.log.client.CoreServiceClient
import com.github.logboard.log.repository.LocalApiKeyRepository
import com.github.logboard.log.model.MembershipResult
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.BeforeEach
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Date

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractIntegrationTest {

    @MockBean
    protected lateinit var coreServiceClient: CoreServiceClient

    @MockBean
    protected lateinit var localApiKeyRepository: LocalApiKeyRepository

    @BeforeEach
    fun setUpDefaultMembership() {
        given(coreServiceClient.getMembership(any(), any())).willReturn(MembershipResult.Found("OWNER"))
    }

    companion object {
        private const val JWT_SECRET = "testSecretKeyForLogBoardIntegrationTestsWhichIsVeryLongAndSecure12345678"

        @Container
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true)

        @Container
        val clickHouse: GenericContainer<*> = GenericContainer(DockerImageName.parse("clickhouse/clickhouse-server:23.8-alpine"))
            .withExposedPorts(8123)
            .withEnv("CLICKHOUSE_DB", "default")
            .withEnv("CLICKHOUSE_USER", "default")
            .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
            .waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofSeconds(60)))
            .withReuse(true)

        @Container
        val elasticsearch: ElasticsearchContainer = ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.0")
        ).apply {
            withEnv("xpack.security.enabled", "false")
            withEnv("discovery.type", "single-node")
            withReuse(true)
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("clickhouse.url") {
                "jdbc:clickhouse://${clickHouse.host}:${clickHouse.getMappedPort(8123)}/default"
            }
            registry.add("spring.elasticsearch.uris") {
                "http://${elasticsearch.host}:${elasticsearch.getMappedPort(9200)}"
            }
        }

        fun makeToken(userId: Long): String {
            val key = Keys.hmacShaKeyFor(JWT_SECRET.toByteArray())
            return Jwts.builder()
                .claim("id", userId)
                .setIssuedAt(Date())
                .setExpiration(Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact()
        }
    }
}
